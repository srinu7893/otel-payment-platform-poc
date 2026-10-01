package com.srinu.payments.gateway.service;

import com.srinu.payments.gateway.api.RefundAuthorizationRequest;
import com.srinu.payments.gateway.client.BankClient;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefundAuthorizationServiceTest {
    @Test
    void returnsCompletedAndBankTransactionIdWhenRefundSucceeds() {
        var bank = mock(BankClient.class);
        var refundId = UUID.randomUUID();
        var paymentId = UUID.randomUUID();
        var transactionId = UUID.randomUUID();
        when(bank.refund(refundId, paymentId, "ACC1001", new BigDecimal("20.00")))
            .thenReturn(new BankClient.BankRefundResponse(transactionId, "COMPLETED", "Refund credited"));

        var service = new RefundAuthorizationService(bank, passThroughCircuitBreakerFactory());
        var result = service.authorize(new RefundAuthorizationRequest(
            refundId, paymentId, "ACC1001", new BigDecimal("20.00")));

        assertEquals("COMPLETED", result.status());
        assertEquals(transactionId, result.bankTransactionId());
    }

    @Test
    void returnsUnknownWhenBankOutcomeCannotBeObserved() {
        var bank = mock(BankClient.class);
        when(bank.refund(any(), any(), anyString(), any())).thenThrow(new RuntimeException("timeout"));

        var service = new RefundAuthorizationService(bank, passThroughCircuitBreakerFactory());
        var result = service.authorize(new RefundAuthorizationRequest(
            UUID.randomUUID(), UUID.randomUUID(), "ACC1001", new BigDecimal("20.00")));

        assertEquals("UNKNOWN", result.status());
        assertEquals("Bank refund outcome requires reconciliation", result.message());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private CircuitBreakerFactory<?, ?> passThroughCircuitBreakerFactory() {
        CircuitBreakerFactory factory = mock(CircuitBreakerFactory.class);
        CircuitBreaker breaker = mock(CircuitBreaker.class);
        when(factory.create(anyString())).thenReturn(breaker);
        when(breaker.run(any(Supplier.class), any(Function.class))).thenAnswer(invocation -> {
            Supplier supplier = invocation.getArgument(0);
            Function fallback = invocation.getArgument(1);
            try { return supplier.get(); }
            catch (Throwable throwable) { return fallback.apply(throwable); }
        });
        return factory;
    }
}
