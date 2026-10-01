package com.srinu.payments.gateway.service;

import com.srinu.payments.gateway.api.TransferAuthorizationRequest;
import com.srinu.payments.gateway.client.BankClient;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransferAuthorizationServiceTest {

    @Test
    void returnsCompletedWhenBankApprovesTransfer() {
        var bank = mock(BankClient.class);
        var paymentId = UUID.randomUUID();
        var transactionId = UUID.randomUUID();
        when(bank.transfer(paymentId, "ACC1001", "ACC2001", new BigDecimal("10.00"), "INR"))
            .thenReturn(new BankClient.BankTransferResponse(transactionId, "COMPLETED", "Transfer completed"));

        var service = new TransferAuthorizationService(bank, passThroughCircuitBreakerFactory());
        var result = service.authorize(new TransferAuthorizationRequest(
            paymentId, "ACC1001", "ACC2001", new BigDecimal("10.00"), "INR"));

        assertEquals("COMPLETED", result.status());
        assertEquals(transactionId, result.bankTransactionId());
    }

    @Test
    void returnsUnknownThroughCircuitBreakerFallbackWhenBankThrows() {
        var bank = mock(BankClient.class);
        when(bank.transfer(any(), anyString(), anyString(), any(), anyString()))
            .thenThrow(new RuntimeException("bank unavailable"));

        var service = new TransferAuthorizationService(bank, passThroughCircuitBreakerFactory());
        var result = service.authorize(new TransferAuthorizationRequest(
            UUID.randomUUID(), "ACC1001", "ACC2001", new BigDecimal("10.00"), "INR"));

        assertEquals("UNKNOWN", result.status());
        assertEquals("Bank transfer outcome requires reconciliation", result.message());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private CircuitBreakerFactory<?, ?> passThroughCircuitBreakerFactory() {
        CircuitBreakerFactory factory = mock(CircuitBreakerFactory.class);
        CircuitBreaker breaker = mock(CircuitBreaker.class);
        when(factory.create(anyString())).thenReturn(breaker);
        when(breaker.run(any(Supplier.class), any(Function.class))).thenAnswer(invocation -> {
            Supplier supplier = invocation.getArgument(0);
            Function fallback = invocation.getArgument(1);
            try {
                return supplier.get();
            } catch (Throwable throwable) {
                return fallback.apply(throwable);
            }
        });
        return factory;
    }
}
