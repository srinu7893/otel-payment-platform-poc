package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.PaymentRequest;
import com.srinu.payments.payment.api.PaymentResponse;
import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Payment;
import com.srinu.payments.payment.domain.PaymentStatus;
import com.srinu.payments.payment.messaging.PaymentEventPublisher;
import com.srinu.payments.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentApplicationService {
    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    private final PaymentRepository repo;
    private final GatewayClient gateway;
    private final PaymentEventPublisher events;

    public PaymentApplicationService(PaymentRepository repo, GatewayClient gateway, PaymentEventPublisher events) {
        this.repo = repo;
        this.gateway = gateway;
        this.events = events;
    }

    @Transactional
    public PaymentResponse create(PaymentRequest req) {
        var existing = repo.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) {
            log.info("event=PAYMENT_IDEMPOTENT_REPLAY paymentId={} idempotencyKey={}", existing.get().getId(), req.idempotencyKey());
            return toResponse(existing.get(), "Idempotent replay");
        }

        var payment = repo.save(new Payment(UUID.randomUUID(), req.idempotencyKey(), req.accountNumber(), req.merchant(), req.amount()));
        log.info("event=PAYMENT_CREATED paymentId={} merchant={} amount={}", payment.getId(), payment.getMerchant(), payment.getAmount());

        try {
            var result = gateway.authorize(payment.getId(), req.accountNumber(), req.amount());
            switch (result.status()) {
                case "COMPLETED" -> payment.markCompleted();
                case "DECLINED" -> payment.markDeclined("BANK_DECLINED");
                default -> payment.markFailed("GATEWAY_FAILURE");
            }
            repo.save(payment);

            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                events.completed(payment.getId(), payment.getAmount());
                log.info("event=PAYMENT_COMPLETED paymentId={} amount={}", payment.getId(), payment.getAmount());
            } else {
                log.warn("event=PAYMENT_NOT_COMPLETED paymentId={} status={} failureCode={}", payment.getId(), payment.getStatus(), payment.getFailureCode());
            }
            return toResponse(payment, result.message());
        } catch (RuntimeException ex) {
            payment.markFailed("DOWNSTREAM_EXCEPTION");
            repo.save(payment);
            log.error("event=PAYMENT_FAILED paymentId={} errorType={} message={}", payment.getId(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID id) {
        return toResponse(find(id), "Payment retrieved");
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> list(PaymentStatus status, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100));
        var result = status == null ? repo.findAll(pageable) : repo.findAllByStatus(status, pageable);
        return result.map(p -> toResponse(p, "Payment retrieved"));
    }

    @Transactional
    public PaymentResponse cancel(UUID id) {
        var payment = find(id);
        payment.cancel();
        repo.save(payment);
        log.info("event=PAYMENT_CANCELLED paymentId={}", payment.getId());
        return toResponse(payment, "Payment cancelled");
    }

    private Payment find(UUID id) {
        return repo.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private PaymentResponse toResponse(Payment p, String message) {
        return new PaymentResponse(p.getId(), p.getStatus().name(), p.getAmount(), message);
    }
}
