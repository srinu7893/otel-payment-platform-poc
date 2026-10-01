package com.srinu.payments.payment.service;

import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Transfer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TransferReconciliationJobTest {
    @Test
    void safelyReplaysUnknownTransferUsingSameTransferId() {
        var state = mock(TransferStateService.class);
        var gateway = mock(GatewayClient.class);
        var transferId = UUID.randomUUID();
        var bankTransactionId = UUID.randomUUID();

        var candidate = new Transfer(transferId, "k1", "demo-customer", "ACC1001", "ACC2001",
            new BigDecimal("15.00"), "INR");
        candidate.markProcessing();
        candidate.markReconciliationRequired("BANK_TRANSFER_OUTCOME_UNKNOWN");
        when(state.reconciliationBatch(eq(20), any(Instant.class))).thenReturn(List.of(candidate));

        candidate.noteReconciliationAttempt();
        when(state.noteReconciliationAttempt(transferId)).thenReturn(candidate);
        var gatewayResult = new GatewayClient.TransferGatewayResult(bankTransactionId, "COMPLETED", "Transfer completed");
        when(gateway.transfer(transferId, "ACC1001", "ACC2001", new BigDecimal("15.00"), "INR"))
            .thenReturn(gatewayResult);

        var completed = new Transfer(transferId, "k1", "demo-customer", "ACC1001", "ACC2001",
            new BigDecimal("15.00"), "INR");
        completed.markProcessing();
        completed.complete(bankTransactionId);
        when(state.applyGatewayResult(transferId, gatewayResult)).thenReturn(completed);

        var job = new TransferReconciliationJob(state, gateway, 20, 10, 30000);
        job.reconcile();

        verify(gateway).transfer(transferId, "ACC1001", "ACC2001", new BigDecimal("15.00"), "INR");
        verify(state).applyGatewayResult(transferId, gatewayResult);
    }

    @Test
    void stopsAutomaticRecoveryAtConfiguredAttemptLimit() {
        var state = mock(TransferStateService.class);
        var gateway = mock(GatewayClient.class);
        var transfer = new Transfer(UUID.randomUUID(), "k2", "demo-customer", "ACC1001", "ACC2001",
            new BigDecimal("15.00"), "INR");
        transfer.markProcessing();
        transfer.markReconciliationRequired("BANK_TRANSFER_OUTCOME_UNKNOWN");
        for (int i = 0; i < 10; i++) transfer.noteReconciliationAttempt();
        when(state.reconciliationBatch(eq(20), any(Instant.class))).thenReturn(List.of(transfer));

        var job = new TransferReconciliationJob(state, gateway, 20, 10, 30000);
        job.reconcile();

        verifyNoInteractions(gateway);
        verify(state, never()).noteReconciliationAttempt(any());
    }
}
