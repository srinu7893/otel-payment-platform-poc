package com.srinu.payments.payment.service;

import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "payment.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private final PaymentStateService state;
    private final GatewayClient gateway;
    private final int batchSize;
    private final int maxAutomaticAttempts;
    private final long processingStaleMs;

    public PaymentReconciliationJob(PaymentStateService state,
                                    GatewayClient gateway,
                                    @Value("${payment.reconciliation.batch-size:20}") int batchSize,
                                    @Value("${payment.reconciliation.max-automatic-attempts:10}") int maxAutomaticAttempts,
                                    @Value("${payment.reconciliation.processing-stale-ms:30000}") long processingStaleMs) {
        this.state = state;
        this.gateway = gateway;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
        this.maxAutomaticAttempts = Math.max(1, maxAutomaticAttempts);
        this.processingStaleMs = Math.max(5000, processingStaleMs);
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.fixed-delay-ms:15000}")
    public void reconcile() {
        var cutoff = Instant.now().minusMillis(processingStaleMs);
        for (var candidate : state.reconciliationBatch(batchSize, cutoff)) {
            try {
                if (candidate.getReconciliationAttempts() >= maxAutomaticAttempts) {
                    log.error("event=PAYMENT_RECONCILIATION_MANUAL_REQUIRED paymentId={} status={} attempts={}",
                        candidate.getId(), candidate.getStatus(), candidate.getReconciliationAttempts());
                    continue;
                }

                var payment = state.noteReconciliationAttempt(candidate.getId());
                if (isTerminal(payment.getStatus())) {
                    continue;
                }

                log.info("event=PAYMENT_RECONCILIATION_STARTED paymentId={} status={} attempt={} account={}",
                    payment.getId(), payment.getStatus(), payment.getReconciliationAttempts(), mask(payment.getAccountNumber()));

                // Reusing the same paymentId is safe because Mock Bank stores a unique debit ledger by paymentId.
                var result = gateway.authorize(payment.getId(), payment.getAccountNumber(), payment.getAmount());
                var finalized = state.applyGatewayResult(payment.getId(), result);

                log.info("event=PAYMENT_RECONCILIATION_RESULT paymentId={} status={} bankTransactionId={} attempt={}",
                    finalized.getId(), finalized.getStatus(), finalized.getBankTransactionId(),
                    finalized.getReconciliationAttempts());
            } catch (RuntimeException ex) {
                log.warn("event=PAYMENT_RECONCILIATION_EXCEPTION paymentId={} errorType={} message={}",
                    candidate.getId(), ex.getClass().getSimpleName(), ex.getMessage());
                try {
                    state.markReconciliationRequired(candidate.getId(), "RECONCILIATION_ATTEMPT_FAILED");
                } catch (RuntimeException stateFailure) {
                    log.error("event=PAYMENT_RECONCILIATION_STATE_UPDATE_FAILED paymentId={} errorType={} message={}",
                        candidate.getId(), stateFailure.getClass().getSimpleName(), stateFailure.getMessage());
                }
            }
        }
    }

    private boolean isTerminal(PaymentStatus status) {
        return status == PaymentStatus.COMPLETED
            || status == PaymentStatus.DECLINED
            || status == PaymentStatus.FAILED
            || status == PaymentStatus.CANCELLED;
    }

    private String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}
