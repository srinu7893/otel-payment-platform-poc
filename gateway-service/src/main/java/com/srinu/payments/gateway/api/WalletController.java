package com.srinu.payments.gateway.api;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import java.util.Map;
import org.slf4j.MDC;

/** Internal gateway-to-bank adapter using the existing workload identity interceptor. */
@RestController @RequestMapping("/api/v1/wallet")
public class WalletController {
    private final RestClient bank;
    public WalletController(RestClient.Builder builder,@Value("${clients.bank.base-url}") String url){bank=builder.baseUrl(url).build();}
    @GetMapping("/{account}") public Object balance(@PathVariable String account){return get("/api/v1/bank/wallet/{account}",account);}
    @GetMapping("/recipients/{account}") public Object recipient(@PathVariable String account){return get("/api/v1/bank/wallet/recipients/{account}",account);}
    @GetMapping("/{account}/activity") public Object activity(@PathVariable String account,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return bank.get().uri("/api/v1/bank/wallet/{account}/activity?page={page}&size={size}",account,page,size).header("X-Correlation-Id",correlation()).retrieve().body(Object.class);
    }
    @PostMapping("/{account}/deposits") public Object deposit(@PathVariable String account,@RequestBody Map<String,Object> request){
        return bank.post().uri("/api/v1/bank/wallet/{account}/deposits",account).header("X-Correlation-Id",correlation()).body(request).retrieve().body(Object.class);
    }
    private Object get(String path,String account){return bank.get().uri(path,account).header("X-Correlation-Id",correlation()).retrieve().body(Object.class);}
    private String correlation(){return java.util.Objects.toString(MDC.get("correlationId"),java.util.UUID.randomUUID().toString());}
}
