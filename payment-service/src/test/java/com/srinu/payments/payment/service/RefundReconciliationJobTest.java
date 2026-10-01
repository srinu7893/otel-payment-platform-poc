package com.srinu.payments.payment.service;

import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Payment;
import com.srinu.payments.payment.domain.Refund;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RefundReconciliationJobTest {
    @Test
    void safelyReplaysUnknownRefundUsingSameRefundId() {
        var state = mock(RefundStateService.class);
        var payments = mock(PaymentStateService.class);
        var gateway = mock(GatewayClient.class);
        var refundId = UUID.randomUUID();
        var paymentId = UUID.randomUUID();
        var bankTx = UUID.randomUUID();

        var candidate = new Refund(refundId, paymentId, "rk", "demo-customer", new BigDecimal("25.00"));
        candidate.markReconciliationRequired("BANK_REFUND_OUTCOME_UNKNOWN");
        when(state.reconciliationBatch(eq(20), any(Instant.class))).thenReturn(List.of(candidate));
        candidate.noteReconciliationAttempt();
        when(state.noteReconciliationAttempt(refundId)).thenReturn(candidate);

        var payment = new Payment(paymentId, "pk", "demo-customer", "ACC1001", "Store", new BigDecimal("25.00"));
        payment.markProcessing();
        payment.markCompleted(UUID.randomUUID());
        when(payments.find(paymentId)).thenReturn(payment);

        var gatewayResult = new GatewayClient.RefundGatewayResult(bankTx, "COMPLETED", "Refund credited");
        when(gateway.refund(refundId, paymentId, "ACC1001", new BigDecimal("25.00"))).thenReturn(gatewayResult);
        var completed = new Refund(refundId, paymentId, "rk", "demo-customer", new BigDecimal("25.00"));
        completed.complete(bankTx);
        when(state.applyGatewayResult(refundId, gatewayResult)).thenReturn(completed);

        var job = new RefundReconciliationJob(state, payments, gateway, 20, 10, 30000);
        job.reconcile();

        verify(gateway).refund(refundId, paymentId, "ACC1001", new BigDecimal("25.00"));
        verify(state).applyGatewayResult(refundId, gatewayResult);
    }

    @Test
    void stopsAutomaticRecoveryAtAttemptLimit() {
        var state = mock(RefundStateService.class);
        var payments = mock(PaymentStateService.class);
        var gateway = mock(GatewayClient.class);
        var refund = new Refund(UUID.randomUUID(), UUID.randomUUID(), "rk2", "demo-customer", new BigDecimal("25.00"));
        refund.markReconciliationRequired("BANK_REFUND_OUTCOME_UNKNOWN");
        for (int i = 0; i < 10; i++) refund.noteReconciliationAttempt();
        when(state.reconciliationBatch(eq(20), any(Instant.class))).thenReturn(List.of(refund));

        var job = new RefundReconciliationJob(state, payments, gateway, 20, 10, 30000);
        job.reconcile();

        verifyNoInteractions(gateway, payments);
        verify(state, never()).noteReconciliationAttempt(any());
    }
}
