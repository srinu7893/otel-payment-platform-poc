package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.PaymentRequest;
import com.srinu.payments.payment.api.PaymentResponse;
import com.srinu.payments.payment.client.CustomerClient;
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
    private final CustomerClient customers;
    private final GatewayClient gateway;
    private final PaymentEventPublisher events;

    public PaymentApplicationService(PaymentRepository repo, CustomerClient customers,
                                     GatewayClient gateway, PaymentEventPublisher events) {
        this.repo = repo;
        this.customers = customers;
        this.gateway = gateway;
        this.events = events;
    }

    @Transactional
    public PaymentResponse create(String customerId, PaymentRequest req) {
        var existing = repo.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) {
            authorizeOwnership(customerId, existing.get(), false);
            log.info("event=PAYMENT_IDEMPOTENT_REPLAY paymentId={} customerId={} idempotencyKey={}",
                existing.get().getId(), customerId, req.idempotencyKey());
            return toResponse(existing.get(), "Idempotent replay");
        }

        var customer = customers.get(customerId);
        if (customer == null || !customer.active()) {
            throw new PaymentAuthorizationException("Customer is not active");
        }
        if (!req.accountNumber().equals(customer.accountNumber())) {
            log.warn("event=PAYMENT_OWNERSHIP_REJECTED customerId={} account={}", customerId, mask(req.accountNumber()));
            throw new PaymentAuthorizationException("Payment account does not belong to authenticated customer");
        }

        var payment = repo.save(new Payment(UUID.randomUUID(), req.idempotencyKey(), customerId,
            req.accountNumber(), req.merchant(), req.amount()));
        log.info("event=PAYMENT_CREATED paymentId={} customerId={} merchant={} amount={} account={}",
            payment.getId(), customerId, payment.getMerchant(), payment.getAmount(), mask(payment.getAccountNumber()));

        try {
            var result = gateway.authorize(payment.getId(), req.accountNumber(), req.amount());
            switch (result.status()) {
                case "COMPLETED" -> payment.markCompleted();
                case "DECLINED" -> payment.markDeclined("BANK_DECLINED");
                default -> payment.markFailed("GATEWAY_FAILURE");
            }
            repo.save(payment);

            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                events.completed(payment.getId(), customerId, payment.getAmount());
                log.info("event=PAYMENT_COMPLETED paymentId={} customerId={} amount={}",
                    payment.getId(), customerId, payment.getAmount());
            } else {
                log.warn("event=PAYMENT_NOT_COMPLETED paymentId={} customerId={} status={} failureCode={}",
                    payment.getId(), customerId, payment.getStatus(), payment.getFailureCode());
            }
            return toResponse(payment, result.message());
        } catch (RuntimeException ex) {
            payment.markFailed("DOWNSTREAM_EXCEPTION");
            repo.save(payment);
            log.error("event=PAYMENT_FAILED paymentId={} customerId={} errorType={} message={}",
                payment.getId(), customerId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(String customerId, UUID id, boolean privileged) {
        var payment = find(id);
        authorizeOwnership(customerId, payment, privileged);
        return toResponse(payment, "Payment retrieved");
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> list(String customerId, boolean privileged, PaymentStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Payment> result;
        if (privileged) {
            result = status == null ? repo.findAll(pageable) : repo.findAllByStatus(status, pageable);
        } else {
            result = status == null
                ? repo.findAllByCustomerId(customerId, pageable)
                : repo.findAllByCustomerIdAndStatus(customerId, status, pageable);
        }
        return result.map(p -> toResponse(p, "Payment retrieved"));
    }

    @Transactional
    public PaymentResponse cancel(String customerId, UUID id, boolean privileged) {
        var payment = find(id);
        authorizeOwnership(customerId, payment, privileged);
        payment.cancel();
        repo.save(payment);
        log.info("event=PAYMENT_CANCELLED paymentId={} customerId={} privileged={}", payment.getId(), customerId, privileged);
        return toResponse(payment, "Payment cancelled");
    }

    private Payment find(UUID id) {
        return repo.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private void authorizeOwnership(String customerId, Payment payment, boolean privileged) {
        if (!privileged && !payment.getCustomerId().equals(customerId)) {
            throw new PaymentAuthorizationException("Payment does not belong to authenticated customer");
        }
    }

    private PaymentResponse toResponse(Payment p, String message) {
        return new PaymentResponse(p.getId(), p.getStatus().name(), p.getAmount(), p.getMerchant(),
            mask(p.getAccountNumber()), p.getFailureCode(), message, p.getCreatedAt());
    }

    private String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }

    public static class PaymentAuthorizationException extends RuntimeException {
        public PaymentAuthorizationException(String message) { super(message); }
    }
}
