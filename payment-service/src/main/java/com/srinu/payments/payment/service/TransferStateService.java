package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.TransferRequest;
import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Transfer;
import com.srinu.payments.payment.messaging.PaymentEventPublisher;
import com.srinu.payments.payment.repository.TransferRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferStateService {
    private final TransferRepository transfers;
    private final PaymentEventPublisher events;

    public TransferStateService(TransferRepository transfers, PaymentEventPublisher events) {
        this.transfers = transfers;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Optional<Transfer> findByIdempotencyKey(String key) {
        return transfers.findByIdempotencyKey(key);
    }

    @Transactional
    public Transfer createProcessing(String customerId, TransferRequest request) {
        var transfer = new Transfer(UUID.randomUUID(), request.idempotencyKey(), customerId,
            request.senderAccount(), request.receiverAccount(), request.amount(), request.currency());
        transfer.markProcessing();
        return transfers.saveAndFlush(transfer);
    }

    @Transactional
    public Transfer applyGatewayResult(UUID transferId, GatewayClient.TransferGatewayResult result) {
        var transfer = transfers.findById(transferId)
            .orElseThrow(() -> new TransferApplicationService.TransferNotFoundException(transferId));

        if (isTerminal(transfer.getStatus())) {
            return transfer;
        }

        switch (result.status()) {
            case "COMPLETED" -> {
                transfer.complete(result.bankTransactionId());
                events.transferCompleted(transfer.getId(), transfer.getBankTransactionId(), transfer.getCustomerId(),
                    transfer.getSenderAccount(), transfer.getReceiverAccount(), transfer.getAmount(), transfer.getCurrency());
            }
            case "FAILED" -> transfer.fail("BANK_TRANSFER_FAILED");
            case "UNKNOWN" -> transfer.markReconciliationRequired("BANK_TRANSFER_OUTCOME_UNKNOWN");
            default -> transfer.markReconciliationRequired("UNRECOGNIZED_GATEWAY_STATUS");
        }
        return transfers.save(transfer);
    }

    @Transactional
    public Transfer markReconciliationRequired(UUID transferId, String code) {
        var transfer = transfers.findById(transferId)
            .orElseThrow(() -> new TransferApplicationService.TransferNotFoundException(transferId));
        if (!isTerminal(transfer.getStatus())) {
            transfer.markReconciliationRequired(code);
            return transfers.save(transfer);
        }
        return transfer;
    }

    @Transactional
    public Transfer noteReconciliationAttempt(UUID transferId) {
        var transfer = transfers.findById(transferId)
            .orElseThrow(() -> new TransferApplicationService.TransferNotFoundException(transferId));
        if (!isTerminal(transfer.getStatus())) {
            transfer.noteReconciliationAttempt();
            return transfers.save(transfer);
        }
        return transfer;
    }

    @Transactional(readOnly = true)
    public List<Transfer> reconciliationBatch(int batchSize, Instant staleProcessingBefore) {
        int safeBatch = Math.max(1, Math.min(batchSize, 100));
        var selected = new ArrayList<Transfer>(safeBatch);
        selected.addAll(transfers.findAllByStatus("RECONCILIATION_REQUIRED", PageRequest.of(0, safeBatch)).getContent());

        int remaining = safeBatch - selected.size();
        if (remaining > 0) {
            selected.addAll(transfers.findAllByStatusAndUpdatedAtBefore(
                "PROCESSING", staleProcessingBefore, PageRequest.of(0, remaining)).getContent());
        }
        return List.copyOf(selected);
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }
}
