package com.srinu.payments.gateway.client;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class BankClient {
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private final RestClient client;

    public BankClient(RestClient.Builder builder,
                      @Value("${clients.bank.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public BankDebitResponse debit(UUID paymentId, String accountNumber, BigDecimal amount) {
        return client.post()
            .uri("/api/v1/bank/debits")
            .header(CORRELATION_HEADER, correlationId())
            .body(new BankDebitRequest(paymentId, accountNumber, amount))
            .retrieve()
            .body(BankDebitResponse.class);
    }

    public BankTransferResponse transfer(UUID paymentId, String senderAccount, String receiverAccount,
                                         BigDecimal amount, String currency) {
        return client.post()
            .uri("/api/v1/bank/transfers")
            .header(CORRELATION_HEADER, correlationId())
            .body(new BankTransferRequest(paymentId, senderAccount, receiverAccount, amount, currency))
            .retrieve()
            .body(BankTransferResponse.class);
    }

    private String correlationId() {
        String value = MDC.get("correlationId");
        return value == null ? UUID.randomUUID().toString() : value;
    }

    public record BankDebitRequest(UUID paymentId, String accountNumber, BigDecimal amount) {}
    public record BankDebitResponse(UUID transactionId, String status, String message) {}
    public record BankTransferRequest(UUID paymentId, String senderAccount, String receiverAccount,
                                      BigDecimal amount, String currency) {}
    public record BankTransferResponse(UUID transactionId, String status, String message) {}
}
