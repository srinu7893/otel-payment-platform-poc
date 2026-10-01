package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.RefundRequest;
import com.srinu.payments.payment.api.RefundResponse;
import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Refund;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RefundApplicationService {
    private static final Logger log = LoggerFactory.getLogger(RefundApplicationService.class);

    private final PaymentStateService payments;
    private final RefundStateService state;
    private final GatewayClient gateway;

    public RefundApplicationService(PaymentStateService payments, RefundStateService state, GatewayClient gateway) {
        this.payments = payments;
        this.state = state;
        this.gateway = gateway;
    }

    public RefundResponse create(String customerId, UUID paymentId, RefundRequest request) {
        var replay = state.findByIdempotencyKey(request.idempotencyKey());
        if (replay.isPresent()) {
            authorizeOwnership(customerId, paymentId, replay.get());
            return toResponse(replay.get(), "Idempotent replay");
        }

        // Read payment before creating refund so the remote call can use immutable account/amount data.
        var payment = payments.find(paymentId);
        if (!payment.getCustomerId().equals(customerId)) {
            throw new PaymentApplicationService.PaymentAuthorizationException("Payment does not belong to authenticated customer");
        }

        final Refund refund;
        try {
            refund = state.createProcessing(paymentId, customerId, request.idempotencyKey());
        } catch (DataIntegrityViolationException race) {
            var existing = state.findByPaymentId(paymentId).orElseThrow(() -> race);
            authorizeOwnership(customerId, paymentId, existing);
            return toResponse(existing, "Existing refund returned");
        }

        log.info("event=REFUND_PROCESSING refundId={} paymentId={} customerId={} amount={} account={}",
            refund.getId(), paymentId, customerId, refund.getAmount(), mask(payment.getAccountNumber()));

        try {
            // Remote credit runs outside local DB transaction; bank replay is safe by refundId.
            var result = gateway.refund(refund.getId(), paymentId, payment.getAccountNumber(), refund.getAmount());
            var finalized = state.applyGatewayResult(refund.getId(), result);
            log.info("event=REFUND_RESULT refundId={} paymentId={} status={} bankTransactionId={}",
                finalized.getId(), paymentId, finalized.getStatus(), finalized.getBankTransactionId());
            return toResponse(finalized, result.message());
        } catch (RuntimeException ex) {
            log.error("event=REFUND_GATEWAY_EXCEPTION refundId={} paymentId={} errorType={} message={}",
                refund.getId(), paymentId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            var unknown = state.markReconciliationRequired(refund.getId(), "DOWNSTREAM_OUTCOME_UNKNOWN");
            return toResponse(unknown, "Refund outcome pending reconciliation");
        }
    }

    public RefundResponse get(String customerId, UUID paymentId) {
        var refund = state.findByPaymentId(paymentId)
            .orElseThrow(() -> new RefundStateService.RefundNotFoundException(paymentId));
        authorizeOwnership(customerId, paymentId, refund);
        return toResponse(refund, "Refund retrieved");
    }

    private void authorizeOwnership(String customerId, UUID paymentId, Refund refund) {
        if (!refund.getCustomerId().equals(customerId) || !refund.getPaymentId().equals(paymentId)) {
            throw new PaymentApplicationService.PaymentAuthorizationException("Refund does not belong to authenticated customer/payment");
        }
    }

    private RefundResponse toResponse(Refund refund, String message) {
        return new RefundResponse(refund.getId(), refund.getPaymentId(), refund.getBankTransactionId(),
            refund.getAmount(), refund.getStatus(), refund.getFailureCode(), message, refund.getCreatedAt());
    }

    private String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}
