package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.TransferRequest;
import com.srinu.payments.payment.api.TransferResponse;
import com.srinu.payments.payment.client.CustomerClient;
import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Transfer;
import com.srinu.payments.payment.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransferApplicationService {
    private static final Logger log = LoggerFactory.getLogger(TransferApplicationService.class);

    private final TransferRepository transfers;
    private final CustomerClient customers;
    private final GatewayClient gateway;
    private final TransferStateService state;

    public TransferApplicationService(TransferRepository transfers, CustomerClient customers,
                                      GatewayClient gateway, TransferStateService state) {
        this.transfers = transfers;
        this.customers = customers;
        this.gateway = gateway;
        this.state = state;
    }

    public TransferResponse create(String customerId, TransferRequest request) {
        var replay = state.findByIdempotencyKey(request.idempotencyKey());
        if (replay.isPresent()) {
            return replay(customerId, replay.get());
        }

        // Customer HTTP lookup intentionally runs without a local DB transaction.
        var customer = customers.get(customerId);
        if (customer == null || !customer.active()) {
            throw new TransferAuthorizationException("Customer is not active");
        }
        if (!request.senderAccount().equals(customer.accountNumber())) {
            log.warn("event=TRANSFER_OWNERSHIP_REJECTED customerId={} sender={}", customerId, mask(request.senderAccount()));
            throw new TransferAuthorizationException("Sender account does not belong to authenticated customer");
        }
        if (request.senderAccount().equals(request.receiverAccount())) {
            throw new IllegalArgumentException("Sender and receiver accounts must be different");
        }

        final Transfer transfer;
        try {
            transfer = state.createProcessing(customerId, request);
        } catch (DataIntegrityViolationException duplicateRace) {
            var existing = state.findByIdempotencyKey(request.idempotencyKey()).orElseThrow(() -> duplicateRace);
            return replay(customerId, existing);
        }

        log.info("event=TRANSFER_PROCESSING transferId={} customerId={} sender={} receiver={} amount={} currency={}",
            transfer.getId(), customerId, mask(request.senderAccount()), mask(request.receiverAccount()),
            request.amount(), request.currency());

        try {
            // Remote Gateway/Bank call intentionally runs after the create transaction has committed.
            var result = gateway.transfer(transfer.getId(), request.senderAccount(), request.receiverAccount(),
                request.amount(), request.currency());
            var finalized = state.applyGatewayResult(transfer.getId(), result);

            if ("COMPLETED".equals(finalized.getStatus())) {
                log.info("event=TRANSFER_COMPLETED transferId={} bankTransactionId={}",
                    finalized.getId(), finalized.getBankTransactionId());
            } else if ("RECONCILIATION_REQUIRED".equals(finalized.getStatus())) {
                log.warn("event=TRANSFER_RECONCILIATION_REQUIRED transferId={} failureCode={}",
                    finalized.getId(), finalized.getFailureCode());
            } else {
                log.warn("event=TRANSFER_NOT_COMPLETED transferId={} status={} reason={}",
                    finalized.getId(), finalized.getStatus(), result.message());
            }
            return toResponse(finalized, result.message());
        } catch (RuntimeException ex) {
            log.error("event=TRANSFER_GATEWAY_EXCEPTION transferId={} errorType={} message={}",
                transfer.getId(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
            try {
                var unknown = state.markReconciliationRequired(transfer.getId(), "DOWNSTREAM_OUTCOME_UNKNOWN");
                return toResponse(unknown, "Transfer outcome pending reconciliation");
            } catch (RuntimeException stateFailure) {
                ex.addSuppressed(stateFailure);
                throw ex;
            }
        }
    }

    private TransferResponse replay(String customerId, Transfer transfer) {
        authorizeOwnership(customerId, transfer, false);
        log.info("event=TRANSFER_IDEMPOTENT_REPLAY transferId={} customerId={} status={}",
            transfer.getId(), customerId, transfer.getStatus());
        return toResponse(transfer, "Idempotent replay");
    }

    @Transactional(readOnly = true)
    public TransferResponse get(String customerId, UUID transferId, boolean privileged) {
        var transfer = transfers.findById(transferId).orElseThrow(() -> new TransferNotFoundException(transferId));
        authorizeOwnership(customerId, transfer, privileged);
        return toResponse(transfer, "Transfer retrieved");
    }

    @Transactional(readOnly = true)
    public Page<TransferResponse> list(String customerId, boolean privileged, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        var result = privileged ? transfers.findAll(pageable) : transfers.findAllByCustomerId(customerId, pageable);
        return result.map(t -> toResponse(t, "Transfer retrieved"));
    }

    private void authorizeOwnership(String customerId, Transfer transfer, boolean privileged) {
        if (!privileged && !transfer.getCustomerId().equals(customerId)) {
            throw new TransferAuthorizationException("Transfer does not belong to authenticated customer");
        }
    }

    private TransferResponse toResponse(Transfer transfer, String message) {
        return new TransferResponse(transfer.getId(), transfer.getBankTransactionId(),
            mask(transfer.getSenderAccount()), mask(transfer.getReceiverAccount()),
            transfer.getAmount(), transfer.getCurrency(), transfer.getStatus(), message, transfer.getCreatedAt());
    }

    private String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }

    public static class TransferAuthorizationException extends RuntimeException {
        public TransferAuthorizationException(String message) { super(message); }
    }

    public static class TransferNotFoundException extends RuntimeException {
        public TransferNotFoundException(UUID id) { super("Transfer not found: " + id); }
    }
}
