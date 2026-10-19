package com.srinu.otelpoc.gateway.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Untrusted aggregate browser measurements. Never accepts raw errors or OTLP. */
@RestController
public class BrowserTelemetryController {
    private static final Map<String,Double> LIMITS=Map.of("CLS",5d,"FCP",120000d,"INP",120000d,"LCP",120000d,"TTFB",120000d,"PAGE_VIEW",1d,"JS_ERROR",1d);
    private final Set<String> origins;private final MeterRegistry meters;private final ObjectMapper json;
    private final java.util.function.LongSupplier clock;
    private long window=0;private int received=0;
    @org.springframework.beans.factory.annotation.Autowired
    public BrowserTelemetryController(MeterRegistry meters,ObjectMapper json,
            @Value("${telemetry.rum.allowed-origins:http://localhost:3000}") String origins){
        this(meters,json,origins,System::nanoTime);
    }
    BrowserTelemetryController(MeterRegistry meters,ObjectMapper json,String origins,java.util.function.LongSupplier clock){
        this.meters=meters;this.json=json;
        this.clock=clock;
        this.origins=Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
    private synchronized boolean admit(){
        long now=clock.getAsLong()/60_000_000_000L;
        if(window!=now){window=now;received=0;}
        return ++received<=300;
    }
    @PostMapping(value="/api/v1/telemetry/rum",consumes="application/json")
    public Mono<ResponseEntity<Void>> ingest(ServerHttpRequest request){
        String origin=request.getHeaders().getOrigin();
        if(origin==null || !origins.contains(origin))return Mono.just(ResponseEntity.status(403).build());
        if(!admit())return Mono.just(ResponseEntity.status(429).build());
        if(request.getHeaders().getContentLength()>512)return Mono.just(ResponseEntity.status(413).build());
        return DataBufferUtils.join(request.getBody(),512).map(buffer->{
            try{
                byte[] bytes=new byte[buffer.readableByteCount()];buffer.read(bytes);
                var body=json.readTree(bytes);
                if(!body.isObject() || body.size()!=2 || !body.has("name") || !body.has("value") || !body.get("name").isTextual() || !body.get("value").isNumber())return ResponseEntity.badRequest().<Void>build();
                String name=body.get("name").textValue();double value=body.get("value").doubleValue();
                if(!LIMITS.containsKey(name) || !Double.isFinite(value) || value<0 || value>LIMITS.get(name) || ((name.equals("PAGE_VIEW") || name.equals("JS_ERROR")) && value!=1))return ResponseEntity.badRequest().<Void>build();
                if(name.equals("PAGE_VIEW") || name.equals("JS_ERROR"))meters.counter("poc.browser.events","event",name).increment();
                else io.micrometer.core.instrument.DistributionSummary.builder("poc.browser.measurement")
                    .tags("metric",name,"unit",name.equals("CLS")?"score":"ms")
                    .serviceLevelObjectives(.001,.01,.1,.5,1,5,10,50,100,250,500,1000,2500,5000,10000,30000,120000)
                    .register(meters).record(value);
                return ResponseEntity.noContent().<Void>build();
            }catch(Exception invalid){return ResponseEntity.badRequest().<Void>build();}
            finally{DataBufferUtils.release(buffer);}
        }).defaultIfEmpty(ResponseEntity.badRequest().build()).onErrorResume(DataBufferLimitException.class,e->Mono.just(ResponseEntity.status(413).build()));
    }
}
