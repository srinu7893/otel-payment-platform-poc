package com.srinu.payments.gateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class BankClient {
    private final RestClient client;

    public BankClient(RestClient.Builder builder,
                      @Value("${clients.bank.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public BankDebitResponse debit(UUID paymentId, String accountNumber, BigDecimal amount) {
        return client.post()
                .uri("/api/v1/bank/debits")
                .body(new BankDebitRequest(paymentId, accountNumber, amount))
                .retrieve()
                .body(BankDebitResponse.class);
    }

    public BankTransferResponse transfer(UUID paymentId, String senderAccount, String receiverAccount,
                                         BigDecimal amount, String currency) {
        return client.post()
                .uri("/api/v1/bank/transfers")
                .body(new BankTransferRequest(paymentId, senderAccount, receiverAccount, amount, currency))
                .retrieve()
                .body(BankTransferResponse.class);
    }

    public record BankDebitRequest(UUID paymentId, String accountNumber, BigDecimal amount) {}
    public record BankDebitResponse(String status, String message) {}
    public record BankTransferRequest(UUID paymentId, String senderAccount, String receiverAccount,
                                      BigDecimal amount, String currency) {}
    public record BankTransferResponse(UUID transactionId, String status, String message) {}
}
