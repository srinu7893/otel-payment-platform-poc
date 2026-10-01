package com.srinu.payments.payment.client;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class GatewayClient {
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private final RestClient client;

    public GatewayClient(RestClient.Builder builder, @Value("${clients.gateway.base-url}") String url) {
        this.client = builder.baseUrl(url).build();
    }

    public GatewayResult authorize(UUID paymentId, String account, BigDecimal amount) {
        return client.post()
            .uri("/api/v1/authorizations")
            .header(CORRELATION_HEADER, correlationId())
            .body(new GatewayRequest(paymentId, account, amount))
            .retrieve()
            .body(GatewayResult.class);
    }

    public TransferGatewayResult transfer(UUID paymentId, String senderAccount, String receiverAccount,
                                          BigDecimal amount, String currency) {
        return client.post()
            .uri("/api/v1/transfer-authorizations")
            .header(CORRELATION_HEADER, correlationId())
            .body(new TransferGatewayRequest(paymentId, senderAccount, receiverAccount, amount, currency))
            .retrieve()
            .body(TransferGatewayResult.class);
    }

    public RefundGatewayResult refund(UUID refundId, UUID originalPaymentId, String accountNumber, BigDecimal amount) {
        return client.post()
            .uri("/api/v1/refund-authorizations")
            .header(CORRELATION_HEADER, correlationId())
            .body(new RefundGatewayRequest(refundId, originalPaymentId, accountNumber, amount))
            .retrieve()
            .body(RefundGatewayResult.class);
    }

    private String correlationId() {
        String value = MDC.get("correlationId");
        return value == null ? UUID.randomUUID().toString() : value;
    }

    public record GatewayRequest(UUID paymentId, String accountNumber, BigDecimal amount) {}
    public record GatewayResult(UUID bankTransactionId, String status, String message) {}
    public record TransferGatewayRequest(UUID paymentId, String senderAccount, String receiverAccount,
                                         BigDecimal amount, String currency) {}
    public record TransferGatewayResult(UUID bankTransactionId, String status, String message) {}
    public record RefundGatewayRequest(UUID refundId, UUID originalPaymentId, String accountNumber, BigDecimal amount) {}
    public record RefundGatewayResult(UUID bankTransactionId, String status, String message) {}
}
