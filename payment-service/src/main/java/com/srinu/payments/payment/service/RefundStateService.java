package com.srinu.payments.payment.service;

import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.PaymentStatus;
import com.srinu.payments.payment.domain.Refund;
import com.srinu.payments.payment.messaging.PaymentEventPublisher;
import com.srinu.payments.payment.repository.PaymentRepository;
import com.srinu.payments.payment.repository.RefundRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefundStateService {
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentEventPublisher events;

    public RefundStateService(PaymentRepository payments, RefundRepository refunds, PaymentEventPublisher events) {
        this.payments = payments;
        this.refunds = refunds;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Optional<Refund> findByIdempotencyKey(String key) {
        return refunds.findByIdempotencyKey(key);
    }

    @Transactional(readOnly = true)
    public Optional<Refund> findByPaymentId(UUID paymentId) {
        return refunds.findByPaymentId(paymentId);
    }

    @Transactional
    public Refund createProcessing(UUID paymentId, String customerId, String idempotencyKey) {
        var payment = payments.findById(paymentId)
            .orElseThrow(() -> new PaymentApplicationService.PaymentNotFoundException(paymentId));
        if (!payment.getCustomerId().equals(customerId)) {
            throw new PaymentApplicationService.PaymentAuthorizationException("Payment does not belong to authenticated customer");
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Only COMPLETED payments can be refunded");
        }
        if (refunds.findByPaymentId(paymentId).isPresent()) {
            throw new IllegalStateException("Payment already has a refund");
        }

        return refunds.saveAndFlush(new Refund(UUID.randomUUID(), paymentId, idempotencyKey,
            customerId, payment.getAmount()));
    }

    @Transactional
    public Refund applyGatewayResult(UUID refundId, GatewayClient.RefundGatewayResult result) {
        var refund = refunds.findById(refundId)
            .orElseThrow(() -> new RefundNotFoundException(refundId));
        if (isTerminal(refund.getStatus())) return refund;

        switch (result.status()) {
            case "COMPLETED" -> {
                refund.complete(result.bankTransactionId());
                events.refundCompleted(refund.getId(), refund.getPaymentId(), result.bankTransactionId(),
                    refund.getCustomerId(), refund.getAmount());
            }
            case "FAILED" -> refund.fail("BANK_REFUND_FAILED");
            case "UNKNOWN" -> refund.markReconciliationRequired("BANK_REFUND_OUTCOME_UNKNOWN");
            default -> refund.markReconciliationRequired("UNRECOGNIZED_GATEWAY_STATUS");
        }
        return refunds.save(refund);
    }

    @Transactional
    public Refund markReconciliationRequired(UUID refundId, String code) {
        var refund = refunds.findById(refundId).orElseThrow(() -> new RefundNotFoundException(refundId));
        if (!isTerminal(refund.getStatus())) {
            refund.markReconciliationRequired(code);
            return refunds.save(refund);
        }
        return refund;
    }

    @Transactional
    public Refund noteReconciliationAttempt(UUID refundId) {
        var refund = refunds.findById(refundId).orElseThrow(() -> new RefundNotFoundException(refundId));
        if (!isTerminal(refund.getStatus())) {
            refund.noteReconciliationAttempt();
            return refunds.save(refund);
        }
        return refund;
    }

    @Transactional(readOnly = true)
    public List<Refund> reconciliationBatch(int batchSize, Instant staleProcessingBefore) {
        int safeBatch = Math.max(1, Math.min(batchSize, 100));
        var selected = new ArrayList<Refund>(safeBatch);
        selected.addAll(refunds.findAllByStatus("RECONCILIATION_REQUIRED", PageRequest.of(0, safeBatch)).getContent());
        int remaining = safeBatch - selected.size();
        if (remaining > 0) {
            selected.addAll(refunds.findAllByStatusAndUpdatedAtBefore(
                "PROCESSING", staleProcessingBefore, PageRequest.of(0, remaining)).getContent());
        }
        return List.copyOf(selected);
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }

    public static class RefundNotFoundException extends RuntimeException {
        public RefundNotFoundException(UUID id) { super("Refund not found: " + id); }
    }
}
