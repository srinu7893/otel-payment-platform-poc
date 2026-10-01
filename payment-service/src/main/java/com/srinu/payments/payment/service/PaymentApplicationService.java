package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.PaymentRequest;
import com.srinu.payments.payment.api.PaymentResponse;
import com.srinu.payments.payment.client.CustomerClient;
import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Payment;
import com.srinu.payments.payment.domain.PaymentStatus;
import com.srinu.payments.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final PaymentStateService state;

    public PaymentApplicationService(PaymentRepository repo, CustomerClient customers,
                                     GatewayClient gateway, PaymentStateService state) {
        this.repo = repo;
        this.customers = customers;
        this.gateway = gateway;
        this.state = state;
    }

    public PaymentResponse create(String customerId, PaymentRequest req) {
        var existing = state.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) {
            return replay(customerId, req.idempotencyKey(), existing.get());
        }

        var customer = customers.get(customerId);
        if (customer == null || !customer.active()) {
            throw new PaymentAuthorizationException("Customer is not active");
        }
        if (!req.accountNumber().equals(customer.accountNumber())) {
            log.warn("event=PAYMENT_OWNERSHIP_REJECTED customerId={} account={}", customerId, mask(req.accountNumber()));
            throw new PaymentAuthorizationException("Payment account does not belong to authenticated customer");
        }

        final Payment payment;
        try {
            payment = state.createProcessing(customerId, req);
        } catch (DataIntegrityViolationException duplicateRace) {
            var replay = state.findByIdempotencyKey(req.idempotencyKey()).orElseThrow(() -> duplicateRace);
            return replay(customerId, req.idempotencyKey(), replay);
        }

        log.info("event=PAYMENT_PROCESSING paymentId={} customerId={} merchant={} amount={} account={}",
            payment.getId(), customerId, payment.getMerchant(), payment.getAmount(), mask(payment.getAccountNumber()));

        try {
            var result = gateway.authorize(payment.getId(), req.accountNumber(), req.amount());
            var finalized = state.applyGatewayResult(payment.getId(), result);

            if (finalized.getStatus() == PaymentStatus.COMPLETED) {
                log.info("event=PAYMENT_COMPLETED paymentId={} bankTransactionId={} customerId={} amount={}",
                    finalized.getId(), finalized.getBankTransactionId(), customerId, finalized.getAmount());
            } else if (finalized.getStatus() == PaymentStatus.RECONCILIATION_REQUIRED) {
                log.warn("event=PAYMENT_RECONCILIATION_REQUIRED paymentId={} customerId={} failureCode={}",
                    finalized.getId(), customerId, finalized.getFailureCode());
            } else {
                log.warn("event=PAYMENT_NOT_COMPLETED paymentId={} customerId={} status={} failureCode={}",
                    finalized.getId(), customerId, finalized.getStatus(), finalized.getFailureCode());
            }
            return toResponse(finalized, result.message());
        } catch (RuntimeException ex) {
            log.error("event=PAYMENT_GATEWAY_EXCEPTION paymentId={} customerId={} errorType={} message={}",
                payment.getId(), customerId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            try {
                var unknown = state.markReconciliationRequired(payment.getId(), "DOWNSTREAM_OUTCOME_UNKNOWN");
                return toResponse(unknown, "Payment outcome pending reconciliation");
            } catch (RuntimeException stateFailure) {
                ex.addSuppressed(stateFailure);
                throw ex;
            }
        }
    }

    private PaymentResponse replay(String customerId, String idempotencyKey, Payment payment) {
        authorizeOwnership(customerId, payment, false);
        log.info("event=PAYMENT_IDEMPOTENT_REPLAY paymentId={} customerId={} idempotencyKey={} status={}",
            payment.getId(), customerId, idempotencyKey, payment.getStatus());
        return toResponse(payment, "Idempotent replay");
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
