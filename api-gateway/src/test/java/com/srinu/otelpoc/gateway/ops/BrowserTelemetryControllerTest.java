package com.srinu.otelpoc.gateway.ops;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.assertj.core.api.Assertions.assertThat;

class BrowserTelemetryControllerTest {
    SimpleMeterRegistry meters=new SimpleMeterRegistry();
    WebTestClient client=WebTestClient.bindToController(new BrowserTelemetryController(meters,new ObjectMapper(),"https://frontend.run.app",()->0L)).build();
    void post(String origin,String body,int status){client.post().uri("/api/v1/telemetry/rum").header("Origin",origin).contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange().expectStatus().isEqualTo(status);}
    @Test void acceptsOnlyBoundedUntrustedMeasurements(){
        post("https://frontend.run.app","{\"name\":\"PAGE_VIEW\",\"value\":1}",204);
        post("https://frontend.run.app","{\"name\":\"LCP\",\"value\":100}",204);
        post("https://evil.run.app","{\"name\":\"PAGE_VIEW\",\"value\":1}",403);
        post("https://frontend.run.app","{\"name\":\"PAGE_VIEW\",\"value\":1,\"account\":\"SECRET\"}",400);
        post("https://frontend.run.app","{\"name\":\"SECRET\",\"value\":1}",400);
        post("https://frontend.run.app","{\"name\":\"LCP\",\"value\":120001}",400);
        assertThat(meters.get("poc.browser.events").tag("event","PAGE_VIEW").counter().count()).isEqualTo(1);
        assertThat(meters.getMeters().toString()).doesNotContain("SECRET");
    }
    @Test void rejectsOversizeAndBoundsTotalRequests(){
        post("https://frontend.run.app"," ".repeat(513),413);
        for(int i=0;i<299;i++)post("https://frontend.run.app","{}",400);
        post("https://frontend.run.app","{}",429);
    }
}
