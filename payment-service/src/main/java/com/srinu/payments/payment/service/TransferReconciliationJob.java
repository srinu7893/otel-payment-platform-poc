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
@ConditionalOnProperty(name = "transfer.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class TransferReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(TransferReconciliationJob.class);

    private final TransferStateService state;
    private final GatewayClient gateway;
    private final int batchSize;
    private final int maxAutomaticAttempts;
    private final long processingStaleMs;

    public TransferReconciliationJob(TransferStateService state,
                                     GatewayClient gateway,
                                     @Value("${transfer.reconciliation.batch-size:20}") int batchSize,
                                     @Value("${transfer.reconciliation.max-automatic-attempts:10}") int maxAutomaticAttempts,
                                     @Value("${transfer.reconciliation.processing-stale-ms:30000}") long processingStaleMs) {
        this.state = state;
        this.gateway = gateway;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
        this.maxAutomaticAttempts = Math.max(1, maxAutomaticAttempts);
        this.processingStaleMs = Math.max(5000, processingStaleMs);
    }

    @Scheduled(fixedDelayString = "${transfer.reconciliation.fixed-delay-ms:15000}")
    public void reconcile() {
        var cutoff = Instant.now().minusMillis(processingStaleMs);
        for (var candidate : state.reconciliationBatch(batchSize, cutoff)) {
            try {
                if (candidate.getReconciliationAttempts() >= maxAutomaticAttempts) {
                    log.error("event=TRANSFER_RECONCILIATION_MANUAL_REQUIRED transferId={} status={} attempts={}",
                        candidate.getId(), candidate.getStatus(), candidate.getReconciliationAttempts());
                    continue;
                }

                var transfer = state.noteReconciliationAttempt(candidate.getId());
                if (isTerminal(transfer.getStatus())) continue;

                log.info("event=TRANSFER_RECONCILIATION_STARTED transferId={} status={} attempt={} sender={} receiver={}",
                    transfer.getId(), transfer.getStatus(), transfer.getReconciliationAttempts(),
                    mask(transfer.getSenderAccount()), mask(transfer.getReceiverAccount()));

                // Same transfer id is safe to replay because BankTransferService is idempotent by paymentId.
                var result = gateway.transfer(transfer.getId(), transfer.getSenderAccount(), transfer.getReceiverAccount(),
                    transfer.getAmount(), transfer.getCurrency());
                var finalized = state.applyGatewayResult(transfer.getId(), result);

                log.info("event=TRANSFER_RECONCILIATION_RESULT transferId={} status={} bankTransactionId={} attempt={}",
                    finalized.getId(), finalized.getStatus(), finalized.getBankTransactionId(),
                    finalized.getReconciliationAttempts());
            } catch (RuntimeException ex) {
                log.warn("event=TRANSFER_RECONCILIATION_EXCEPTION transferId={} errorType={} message={}",
                    candidate.getId(), ex.getClass().getSimpleName(), ex.getMessage());
                try {
                    state.markReconciliationRequired(candidate.getId(), "RECONCILIATION_ATTEMPT_FAILED");
                } catch (RuntimeException stateFailure) {
                    log.error("event=TRANSFER_RECONCILIATION_STATE_UPDATE_FAILED transferId={} errorType={} message={}",
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
