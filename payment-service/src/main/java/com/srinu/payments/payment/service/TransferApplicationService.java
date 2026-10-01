package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.TransferRequest;
import com.srinu.payments.payment.api.TransferResponse;
import com.srinu.payments.payment.client.CustomerClient;
import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Transfer;
import com.srinu.payments.payment.messaging.PaymentEventPublisher;
import com.srinu.payments.payment.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final PaymentEventPublisher events;

    public TransferApplicationService(TransferRepository transfers, CustomerClient customers,
                                      GatewayClient gateway, PaymentEventPublisher events) {
        this.transfers = transfers;
        this.customers = customers;
        this.gateway = gateway;
        this.events = events;
    }

    @Transactional
    public TransferResponse create(String customerId, TransferRequest request) {
        var replay = transfers.findByIdempotencyKey(request.idempotencyKey());
        if (replay.isPresent()) {
            log.info("event=TRANSFER_IDEMPOTENT_REPLAY transferId={} customerId={}", replay.get().getId(), customerId);
            return toResponse(replay.get(), "Idempotent replay");
        }

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

        var transfer = transfers.save(new Transfer(UUID.randomUUID(), request.idempotencyKey(), customerId,
            request.senderAccount(), request.receiverAccount(), request.amount(), request.currency()));

        log.info("event=TRANSFER_CREATED transferId={} customerId={} sender={} receiver={} amount={} currency={}",
            transfer.getId(), customerId, mask(request.senderAccount()), mask(request.receiverAccount()),
            request.amount(), request.currency());

        try {
            var result = gateway.transfer(transfer.getId(), request.senderAccount(), request.receiverAccount(),
                request.amount(), request.currency());
            if ("COMPLETED".equals(result.status())) {
                transfer.complete(result.bankTransactionId());
                transfers.save(transfer);
                events.transferCompleted(transfer.getId(), transfer.getBankTransactionId(), customerId,
                    transfer.getSenderAccount(), transfer.getReceiverAccount(), transfer.getAmount(), transfer.getCurrency());
                log.info("event=TRANSFER_COMPLETED transferId={} bankTransactionId={}",
                    transfer.getId(), transfer.getBankTransactionId());
            } else {
                transfer.fail("BANK_TRANSFER_FAILED");
                transfers.save(transfer);
                log.warn("event=TRANSFER_FAILED transferId={} reason={}", transfer.getId(), result.message());
            }
            return toResponse(transfer, result.message());
        } catch (RuntimeException ex) {
            transfer.fail("DOWNSTREAM_EXCEPTION");
            transfers.save(transfer);
            log.error("event=TRANSFER_FAILED transferId={} errorType={} message={}",
                transfer.getId(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw ex;
        }
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
