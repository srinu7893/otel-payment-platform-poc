package com.srinu.payments.payment.service;

import com.srinu.payments.payment.client.GatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "refund.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class RefundReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(RefundReconciliationJob.class);

    private final RefundStateService state;
    private final PaymentStateService payments;
    private final GatewayClient gateway;
    private final int batchSize;
    private final int maxAutomaticAttempts;
    private final long processingStaleMs;

    public RefundReconciliationJob(RefundStateService state,
                                   PaymentStateService payments,
                                   GatewayClient gateway,
                                   @Value("${refund.reconciliation.batch-size:20}") int batchSize,
                                   @Value("${refund.reconciliation.max-automatic-attempts:10}") int maxAutomaticAttempts,
                                   @Value("${refund.reconciliation.processing-stale-ms:30000}") long processingStaleMs) {
        this.state = state;
        this.payments = payments;
        this.gateway = gateway;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
        this.maxAutomaticAttempts = Math.max(1, maxAutomaticAttempts);
        this.processingStaleMs = Math.max(5000, processingStaleMs);
    }

    @Scheduled(fixedDelayString = "${refund.reconciliation.fixed-delay-ms:15000}")
    public void reconcile() {
        var cutoff = Instant.now().minusMillis(processingStaleMs);
        for (var candidate : state.reconciliationBatch(batchSize, cutoff)) {
            try {
                if (candidate.getReconciliationAttempts() >= maxAutomaticAttempts) {
                    log.error("event=REFUND_RECONCILIATION_MANUAL_REQUIRED refundId={} paymentId={} status={} attempts={}",
                        candidate.getId(), candidate.getPaymentId(), candidate.getStatus(), candidate.getReconciliationAttempts());
                    continue;
                }

                var refund = state.noteReconciliationAttempt(candidate.getId());
                if (isTerminal(refund.getStatus())) continue;
                var payment = payments.find(refund.getPaymentId());

                log.info("event=REFUND_RECONCILIATION_STARTED refundId={} paymentId={} attempt={} account={}",
                    refund.getId(), refund.getPaymentId(), refund.getReconciliationAttempts(), mask(payment.getAccountNumber()));

                var result = gateway.refund(refund.getId(), refund.getPaymentId(),
                    payment.getAccountNumber(), refund.getAmount());
                var finalized = state.applyGatewayResult(refund.getId(), result);

                log.info("event=REFUND_RECONCILIATION_RESULT refundId={} paymentId={} status={} bankTransactionId={} attempt={}",
                    finalized.getId(), finalized.getPaymentId(), finalized.getStatus(),
                    finalized.getBankTransactionId(), finalized.getReconciliationAttempts());
            } catch (RuntimeException ex) {
                log.warn("event=REFUND_RECONCILIATION_EXCEPTION refundId={} errorType={} message={}",
                    candidate.getId(), ex.getClass().getSimpleName(), ex.getMessage());
                try {
                    state.markReconciliationRequired(candidate.getId(), "RECONCILIATION_ATTEMPT_FAILED");
                } catch (RuntimeException stateFailure) {
                    log.error("event=REFUND_RECONCILIATION_STATE_UPDATE_FAILED refundId={} errorType={} message={}",
                        candidate.getId(), stateFailure.getClass().getSimpleName(), stateFailure.getMessage());
                }
            }
        }
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }

    private String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}
