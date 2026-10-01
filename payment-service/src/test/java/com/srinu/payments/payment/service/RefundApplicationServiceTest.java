package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.RefundRequest;
import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Payment;
import com.srinu.payments.payment.domain.Refund;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RefundApplicationServiceTest {
    @Test
    void completesFullRefundForOwnedPayment() {
        var payments = mock(PaymentStateService.class);
        var state = mock(RefundStateService.class);
        var gateway = mock(GatewayClient.class);
        var paymentId = UUID.randomUUID();
        var refundId = UUID.randomUUID();
        var bankTx = UUID.randomUUID();
        var request = new RefundRequest("refund-key");

        when(state.findByIdempotencyKey("refund-key")).thenReturn(Optional.empty());
        var payment = new Payment(paymentId, "pay-key", "demo-customer", "ACC1001", "Store", new BigDecimal("25.00"));
        payment.markProcessing();
        payment.markCompleted(UUID.randomUUID());
        when(payments.find(paymentId)).thenReturn(payment);

        var processing = new Refund(refundId, paymentId, "refund-key", "demo-customer", new BigDecimal("25.00"));
        when(state.createProcessing(paymentId, "demo-customer", "refund-key")).thenReturn(processing);
        var gatewayResult = new GatewayClient.RefundGatewayResult(bankTx, "COMPLETED", "Refund credited");
        when(gateway.refund(refundId, paymentId, "ACC1001", new BigDecimal("25.00"))).thenReturn(gatewayResult);

        var completed = new Refund(refundId, paymentId, "refund-key", "demo-customer", new BigDecimal("25.00"));
        completed.complete(bankTx);
        when(state.applyGatewayResult(refundId, gatewayResult)).thenReturn(completed);

        var service = new RefundApplicationService(payments, state, gateway);
        var response = service.create("demo-customer", paymentId, request);

        assertEquals("COMPLETED", response.status());
        assertEquals(bankTx, response.bankTransactionId());
        assertEquals(new BigDecimal("25.00"), response.amount());
    }

    @Test
    void gatewayExceptionReturnsReconciliationRequired() {
        var payments = mock(PaymentStateService.class);
        var state = mock(RefundStateService.class);
        var gateway = mock(GatewayClient.class);
        var paymentId = UUID.randomUUID();
        var refundId = UUID.randomUUID();
        var request = new RefundRequest("refund-key-2");

        when(state.findByIdempotencyKey("refund-key-2")).thenReturn(Optional.empty());
        var payment = new Payment(paymentId, "pay-key", "demo-customer", "ACC1001", "Store", new BigDecimal("25.00"));
        payment.markProcessing();
        payment.markCompleted(UUID.randomUUID());
        when(payments.find(paymentId)).thenReturn(payment);

        var processing = new Refund(refundId, paymentId, "refund-key-2", "demo-customer", new BigDecimal("25.00"));
        when(state.createProcessing(paymentId, "demo-customer", "refund-key-2")).thenReturn(processing);
        when(gateway.refund(refundId, paymentId, "ACC1001", new BigDecimal("25.00")))
            .thenThrow(new RuntimeException("timeout"));

        var unknown = new Refund(refundId, paymentId, "refund-key-2", "demo-customer", new BigDecimal("25.00"));
        unknown.markReconciliationRequired("DOWNSTREAM_OUTCOME_UNKNOWN");
        when(state.markReconciliationRequired(refundId, "DOWNSTREAM_OUTCOME_UNKNOWN")).thenReturn(unknown);

        var service = new RefundApplicationService(payments, state, gateway);
        var response = service.create("demo-customer", paymentId, request);

        assertEquals("RECONCILIATION_REQUIRED", response.status());
        verify(state).markReconciliationRequired(refundId, "DOWNSTREAM_OUTCOME_UNKNOWN");
    }
}
