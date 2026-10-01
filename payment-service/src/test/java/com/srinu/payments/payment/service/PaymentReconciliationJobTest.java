package com.srinu.payments.payment.service;

import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentReconciliationJobTest {
    @Test
    void safelyReplaysUnknownPaymentUsingSamePaymentId() {
        var state = mock(PaymentStateService.class);
        var gateway = mock(GatewayClient.class);
        var paymentId = UUID.randomUUID();
        var bankTransactionId = UUID.randomUUID();

        var candidate = new Payment(paymentId, "k1", "demo-customer", "ACC1001", "Demo", new BigDecimal("25.00"));
        candidate.markProcessing();
        candidate.markReconciliationRequired("BANK_OUTCOME_UNKNOWN");
        when(state.reconciliationBatch(eq(20), any(Instant.class))).thenReturn(List.of(candidate));

        candidate.noteReconciliationAttempt();
        when(state.noteReconciliationAttempt(paymentId)).thenReturn(candidate);
        var gatewayResult = new GatewayClient.GatewayResult(bankTransactionId, "COMPLETED", "Debit successful");
        when(gateway.authorize(paymentId, "ACC1001", new BigDecimal("25.00"))).thenReturn(gatewayResult);

        var completed = new Payment(paymentId, "k1", "demo-customer", "ACC1001", "Demo", new BigDecimal("25.00"));
        completed.markProcessing();
        completed.markCompleted(bankTransactionId);
        when(state.applyGatewayResult(paymentId, gatewayResult)).thenReturn(completed);

        var job = new PaymentReconciliationJob(state, gateway, 20, 10, 30000);
        job.reconcile();

        verify(gateway).authorize(paymentId, "ACC1001", new BigDecimal("25.00"));
        verify(state).applyGatewayResult(paymentId, gatewayResult);
    }

    @Test
    void stopsAutomaticRecoveryAtConfiguredAttemptLimit() {
        var state = mock(PaymentStateService.class);
        var gateway = mock(GatewayClient.class);
        var payment = new Payment(UUID.randomUUID(), "k2", "demo-customer", "ACC1001", "Demo", new BigDecimal("25.00"));
        payment.markProcessing();
        payment.markReconciliationRequired("BANK_OUTCOME_UNKNOWN");
        for (int i = 0; i < 10; i++) payment.noteReconciliationAttempt();
        when(state.reconciliationBatch(eq(20), any(Instant.class))).thenReturn(List.of(payment));

        var job = new PaymentReconciliationJob(state, gateway, 20, 10, 30000);
        job.reconcile();

        verifyNoInteractions(gateway);
        verify(state, never()).noteReconciliationAttempt(any());
    }
}
