package com.srinu.payments.payment.client;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Component; import org.springframework.web.client.RestClient; import java.math.BigDecimal; import java.util.UUID;
@Component public class GatewayClient {
 private final RestClient client; public GatewayClient(RestClient.Builder b,@Value("${clients.gateway.base-url}") String url){client=b.baseUrl(url).build();}
 public GatewayResult authorize(UUID paymentId,String account,BigDecimal amount){return client.post().uri("/api/v1/authorizations").body(new GatewayRequest(paymentId,account,amount)).retrieve().body(GatewayResult.class);}
 public record GatewayRequest(UUID paymentId,String accountNumber,BigDecimal amount){} public record GatewayResult(String status,String message){}
}