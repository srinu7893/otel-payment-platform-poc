package com.srinu.payments.payment.service;

import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Payment;
import com.srinu.payments.payment.domain.Refund;
import com.srinu.payments.payment.messaging.PaymentEventPublisher;
import com.srinu.payments.payment.repository.PaymentRepository;
import com.srinu.payments.payment.repository.RefundRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefundStateServiceTest {
    @Test
    void onlyCompletedOwnedPaymentCanCreateRefund() {
        var payments = mock(PaymentRepository.class);
        var refunds = mock(RefundRepository.class);
        var events = mock(PaymentEventPublisher.class);
        var paymentId = UUID.randomUUID();
        var payment = new Payment(paymentId, "pay-key", "demo-customer", "ACC1001", "Store", new BigDecimal("25.00"));
        payment.markProcessing();
        payment.markCompleted(UUID.randomUUID());
        when(payments.findById(paymentId)).thenReturn(Optional.of(payment));
        when(refunds.findByPaymentId(paymentId)).thenReturn(Optional.empty());
        when(refunds.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        var service = new RefundStateService(payments, refunds, events);
        var refund = service.createProcessing(paymentId, "demo-customer", "refund-key");

        assertEquals("PROCESSING", refund.getStatus());
        assertEquals(new BigDecimal("25.00"), refund.getAmount());
    }

    @Test
    void rejectsRefundForNonCompletedPayment() {
        var payments = mock(PaymentRepository.class);
        var refunds = mock(RefundRepository.class);
        var events = mock(PaymentEventPublisher.class);
        var paymentId = UUID.randomUUID();
        var payment = new Payment(paymentId, "pay-key", "demo-customer", "ACC1001", "Store", new BigDecimal("25.00"));
        when(payments.findById(paymentId)).thenReturn(Optional.of(payment));

        var service = new RefundStateService(payments, refunds, events);
        assertThrows(IllegalStateException.class,
            () -> service.createProcessing(paymentId, "demo-customer", "refund-key"));
        verify(refunds, never()).saveAndFlush(any());
    }

    @Test
    void completedGatewayResultStagesRefundOutboxEventOnce() {
        var payments = mock(PaymentRepository.class);
        var refunds = mock(RefundRepository.class);
        var events = mock(PaymentEventPublisher.class);
        var refundId = UUID.randomUUID();
        var paymentId = UUID.randomUUID();
        var bankTx = UUID.randomUUID();
        var refund = new Refund(refundId, paymentId, "refund-key", "demo-customer", new BigDecimal("25.00"));
        when(refunds.findById(refundId)).thenReturn(Optional.of(refund));
        when(refunds.save(any())).thenAnswer(i -> i.getArgument(0));

        var service = new RefundStateService(payments, refunds, events);
        var result = service.applyGatewayResult(refundId,
            new GatewayClient.RefundGatewayResult(bankTx, "COMPLETED", "Refund credited"));

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(bankTx, result.getBankTransactionId());
        verify(events).refundCompleted(refundId, paymentId, bankTx, "demo-customer", new BigDecimal("25.00"));

        service.applyGatewayResult(refundId,
            new GatewayClient.RefundGatewayResult(bankTx, "COMPLETED", "Replay"));
        verify(events, times(1)).refundCompleted(any(), any(), any(), anyString(), any());
    }

    @Test
    void unknownGatewayResultRequiresReconciliationWithoutPublishingEvent() {
        var payments = mock(PaymentRepository.class);
        var refunds = mock(RefundRepository.class);
        var events = mock(PaymentEventPublisher.class);
        var refundId = UUID.randomUUID();
        var refund = new Refund(refundId, UUID.randomUUID(), "refund-key", "demo-customer", new BigDecimal("25.00"));
        when(refunds.findById(refundId)).thenReturn(Optional.of(refund));
        when(refunds.save(any())).thenAnswer(i -> i.getArgument(0));

        var service = new RefundStateService(payments, refunds, events);
        var result = service.applyGatewayResult(refundId,
            new GatewayClient.RefundGatewayResult(null, "UNKNOWN", "Bank refund outcome requires reconciliation"));

        assertEquals("RECONCILIATION_REQUIRED", result.getStatus());
        verifyNoInteractions(events);
    }
}
