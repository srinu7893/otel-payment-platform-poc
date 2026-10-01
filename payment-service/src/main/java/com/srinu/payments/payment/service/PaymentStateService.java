package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.PaymentRequest;
import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Payment;
import com.srinu.payments.payment.domain.PaymentStatus;
import com.srinu.payments.payment.messaging.PaymentEventPublisher;
import com.srinu.payments.payment.repository.PaymentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentStateService {
    private final PaymentRepository payments;
    private final PaymentEventPublisher events;

    public PaymentStateService(PaymentRepository payments, PaymentEventPublisher events) {
        this.payments = payments;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findByIdempotencyKey(String key) {
        return payments.findByIdempotencyKey(key);
    }

    @Transactional(readOnly = true)
    public Payment find(UUID paymentId) {
        return payments.findById(paymentId)
            .orElseThrow(() -> new PaymentApplicationService.PaymentNotFoundException(paymentId));
    }

    @Transactional
    public Payment createProcessing(String customerId, PaymentRequest request) {
        var payment = new Payment(UUID.randomUUID(), request.idempotencyKey(), customerId,
            request.accountNumber(), request.merchant(), request.amount());
        payment.markProcessing();
        return payments.saveAndFlush(payment);
    }

    @Transactional
    public Payment applyGatewayResult(UUID paymentId, GatewayClient.GatewayResult result) {
        var payment = payments.findById(paymentId)
            .orElseThrow(() -> new PaymentApplicationService.PaymentNotFoundException(paymentId));

        if (isTerminal(payment.getStatus())) {
            return payment;
        }

        switch (result.status()) {
            case "COMPLETED" -> {
                payment.markCompleted(result.bankTransactionId());
                events.completed(payment.getId(), payment.getCustomerId(), payment.getAmount());
            }
            case "DECLINED" -> payment.markDeclined("BANK_DECLINED");
            case "FAILED" -> payment.markFailed("BANK_DECLINED");
            case "UNKNOWN" -> payment.markReconciliationRequired("BANK_OUTCOME_UNKNOWN");
            default -> payment.markReconciliationRequired("UNRECOGNIZED_GATEWAY_STATUS");
        }
        return payments.save(payment);
    }

    @Transactional
    public Payment markReconciliationRequired(UUID paymentId, String code) {
        var payment = payments.findById(paymentId)
            .orElseThrow(() -> new PaymentApplicationService.PaymentNotFoundException(paymentId));
        if (!isTerminal(payment.getStatus())) {
            payment.markReconciliationRequired(code);
            return payments.save(payment);
        }
        return payment;
    }

    @Transactional
    public Payment noteReconciliationAttempt(UUID paymentId) {
        var payment = payments.findById(paymentId)
            .orElseThrow(() -> new PaymentApplicationService.PaymentNotFoundException(paymentId));
        if (!isTerminal(payment.getStatus())) {
            payment.noteReconciliationAttempt();
            return payments.save(payment);
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public List<Payment> reconciliationBatch(int batchSize, Instant staleProcessingBefore) {
        int safeBatch = Math.max(1, Math.min(batchSize, 100));
        var selected = new ArrayList<Payment>(safeBatch);

        var required = payments.findAllByStatus(PaymentStatus.RECONCILIATION_REQUIRED,
            PageRequest.of(0, safeBatch)).getContent();
        selected.addAll(required);

        int remaining = safeBatch - selected.size();
        if (remaining > 0) {
            selected.addAll(payments.findAllByStatusAndUpdatedAtBefore(
                PaymentStatus.PROCESSING, staleProcessingBefore, PageRequest.of(0, remaining)).getContent());
        }
        return List.copyOf(selected);
    }

    private boolean isTerminal(PaymentStatus status) {
        return status == PaymentStatus.COMPLETED
            || status == PaymentStatus.DECLINED
            || status == PaymentStatus.FAILED
            || status == PaymentStatus.CANCELLED;
    }
}
