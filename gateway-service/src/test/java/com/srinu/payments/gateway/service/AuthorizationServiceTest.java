package com.srinu.payments.gateway.service;

import com.srinu.payments.gateway.api.AuthorizationRequest;
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

class AuthorizationServiceTest {
    @Test
    void returnsCompletedAndBankTransactionIdWhenBankApproves() {
        var bank = mock(BankClient.class);
        var paymentId = UUID.randomUUID();
        var transactionId = UUID.randomUUID();
        when(bank.debit(paymentId, "ACC1001", new BigDecimal("50.00")))
                .thenReturn(new BankClient.BankDebitResponse(transactionId, "COMPLETED", "Debit successful"));

        var service = new AuthorizationService(bank, passThroughCircuitBreakerFactory());
        var result = service.authorize(new AuthorizationRequest(paymentId, "ACC1001", new BigDecimal("50.00")));

        assertEquals("COMPLETED", result.status());
        assertEquals(transactionId, result.bankTransactionId());
    }

    @Test
    void returnsUnknownThroughCircuitBreakerFallbackWhenBankClientThrows() {
        var bank = mock(BankClient.class);
        var paymentId = UUID.randomUUID();
        when(bank.debit(any(), anyString(), any())).thenThrow(new RuntimeException("downstream unavailable"));

        var service = new AuthorizationService(bank, passThroughCircuitBreakerFactory());
        var result = service.authorize(new AuthorizationRequest(paymentId, "ACC1001", new BigDecimal("50.00")));

        assertEquals("UNKNOWN", result.status());
        assertEquals("Bank outcome requires reconciliation", result.message());
        assertEquals(null, result.bankTransactionId());
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
