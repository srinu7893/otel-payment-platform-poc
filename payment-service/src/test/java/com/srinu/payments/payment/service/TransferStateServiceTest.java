package com.srinu.payments.payment.service;

import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Transfer;
import com.srinu.payments.payment.messaging.PaymentEventPublisher;
import com.srinu.payments.payment.repository.TransferRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransferStateServiceTest {
    @Test
    void completedGatewayResultStoresBankTransactionAndStagesOutboxEvent() {
        var repo = mock(TransferRepository.class);
        var events = mock(PaymentEventPublisher.class);
        var transferId = UUID.randomUUID();
        var bankTransactionId = UUID.randomUUID();
        var transfer = new Transfer(transferId, "k1", "demo-customer", "ACC1001", "ACC2001",
            new BigDecimal("25.00"), "INR");
        transfer.markProcessing();

        when(repo.findById(transferId)).thenReturn(Optional.of(transfer));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        var service = new TransferStateService(repo, events);
        var result = service.applyGatewayResult(transferId,
            new GatewayClient.TransferGatewayResult(bankTransactionId, "COMPLETED", "Transfer completed"));

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(bankTransactionId, result.getBankTransactionId());
        verify(events).transferCompleted(transferId, bankTransactionId, "demo-customer",
            "ACC1001", "ACC2001", new BigDecimal("25.00"), "INR");
    }

    @Test
    void unknownGatewayResultMovesTransferToReconciliationWithoutPublishingCompletion() {
        var repo = mock(TransferRepository.class);
        var events = mock(PaymentEventPublisher.class);
        var transferId = UUID.randomUUID();
        var transfer = new Transfer(transferId, "k2", "demo-customer", "ACC1001", "ACC2001",
            new BigDecimal("25.00"), "INR");
        transfer.markProcessing();

        when(repo.findById(transferId)).thenReturn(Optional.of(transfer));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        var service = new TransferStateService(repo, events);
        var result = service.applyGatewayResult(transferId,
            new GatewayClient.TransferGatewayResult(null, "UNKNOWN", "Bank outcome requires reconciliation"));

        assertEquals("RECONCILIATION_REQUIRED", result.getStatus());
        assertEquals("BANK_TRANSFER_OUTCOME_UNKNOWN", result.getFailureCode());
        verifyNoInteractions(events);
    }

    @Test
    void terminalTransferDoesNotPublishCompletionTwice() {
        var repo = mock(TransferRepository.class);
        var events = mock(PaymentEventPublisher.class);
        var transferId = UUID.randomUUID();
        var bankTransactionId = UUID.randomUUID();
        var transfer = new Transfer(transferId, "k3", "demo-customer", "ACC1001", "ACC2001",
            new BigDecimal("25.00"), "INR");
        transfer.markProcessing();
        transfer.complete(bankTransactionId);
        when(repo.findById(transferId)).thenReturn(Optional.of(transfer));

        var service = new TransferStateService(repo, events);
        var result = service.applyGatewayResult(transferId,
            new GatewayClient.TransferGatewayResult(bankTransactionId, "COMPLETED", "Replay"));

        assertEquals("COMPLETED", result.getStatus());
        verifyNoInteractions(events);
        verify(repo, never()).save(any());
    }
}
