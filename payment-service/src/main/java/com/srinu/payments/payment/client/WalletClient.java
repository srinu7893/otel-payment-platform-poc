package com.srinu.payments.payment.client;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;

@Component public class WalletClient {
    private final RestClient client;
    public WalletClient(RestClient.Builder builder,@Value("${clients.gateway.base-url}") String url,
        @Value("${clients.wallet.read-timeout:45s}") java.time.Duration timeout){
        var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(3));factory.setReadTimeout(timeout);
        client=builder.clone().baseUrl(url).requestFactory(factory).build();
    }
    public record Balance(String accountNumber,String customerName,BigDecimal balance,String currency,boolean active) {}
    public record Recipient(String accountNumber,String customerName,boolean active) {}
    public record Deposit(UUID id,String accountNumber,BigDecimal amount,String status) {}
    public record DepositRequest(UUID idempotencyKey,BigDecimal amount) {}
    public record Activity(UUID id,String type,String direction,BigDecimal amount,String currency,String status,Instant createdAt) {}
    public Balance balance(String account){return client.get().uri("/api/v1/wallet/{account}",account).header("X-Correlation-Id",correlation()).retrieve().body(Balance.class);}
    public Recipient recipient(String account){return client.get().uri("/api/v1/wallet/recipients/{account}",account).header("X-Correlation-Id",correlation()).retrieve().body(Recipient.class);}
    public Deposit deposit(String account,DepositRequest request){return client.post().uri("/api/v1/wallet/{account}/deposits",account).header("X-Correlation-Id",correlation()).body(request).retrieve().body(Deposit.class);}
    public List<Activity> activity(String account,int page,int size){return client.get().uri("/api/v1/wallet/{account}/activity?page={page}&size={size}",account,page,size).header("X-Correlation-Id",correlation()).retrieve().body(new ParameterizedTypeReference<List<Activity>>(){});}
    private String correlation(){return java.util.Objects.toString(MDC.get("correlationId"),UUID.randomUUID().toString());}
}
