package com.srinu.payments.payment.service;

import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Payment;
import com.srinu.payments.payment.domain.PaymentStatus;
import com.srinu.payments.payment.messaging.PaymentEventPublisher;
import com.srinu.payments.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentStateServiceTest {
    @Test
    void completedGatewayResultStoresBankTransactionAndStagesOutboxEvent() {
        var repo = mock(PaymentRepository.class);
        var events = mock(PaymentEventPublisher.class);
        var paymentId = UUID.randomUUID();
        var bankTransactionId = UUID.randomUUID();
        var payment = new Payment(paymentId, "k1", "demo-customer", "ACC1001", "Demo Store", new BigDecimal("25.00"));
        payment.markProcessing();

        when(repo.findById(paymentId)).thenReturn(Optional.of(payment));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        var service = new PaymentStateService(repo, events);
        var result = service.applyGatewayResult(paymentId,
            new GatewayClient.GatewayResult(bankTransactionId, "COMPLETED", "Approved"));

        assertEquals(PaymentStatus.COMPLETED, result.getStatus());
        assertEquals(bankTransactionId, result.getBankTransactionId());
        verify(events).completed(paymentId, "demo-customer", new BigDecimal("25.00"));
    }

    @Test
    void unknownGatewayResultMovesPaymentToReconciliationWithoutPublishingCompletion() {
        var repo = mock(PaymentRepository.class);
        var events = mock(PaymentEventPublisher.class);
        var paymentId = UUID.randomUUID();
        var payment = new Payment(paymentId, "k2", "demo-customer", "ACC1001", "Demo Store", new BigDecimal("25.00"));
        payment.markProcessing();

        when(repo.findById(paymentId)).thenReturn(Optional.of(payment));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        var service = new PaymentStateService(repo, events);
        var result = service.applyGatewayResult(paymentId,
            new GatewayClient.GatewayResult(null, "UNKNOWN", "Bank outcome requires reconciliation"));

        assertEquals(PaymentStatus.RECONCILIATION_REQUIRED, result.getStatus());
        assertEquals("BANK_OUTCOME_UNKNOWN", result.getFailureCode());
        verifyNoInteractions(events);
    }
}
