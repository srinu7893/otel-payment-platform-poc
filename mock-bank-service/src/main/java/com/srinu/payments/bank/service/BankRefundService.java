package com.srinu.payments.bank.service;

import com.srinu.payments.bank.api.RefundRequest;
import com.srinu.payments.bank.api.RefundResponse;
import com.srinu.payments.bank.domain.RefundTransaction;
import com.srinu.payments.bank.repository.AccountRepository;
import com.srinu.payments.bank.repository.RefundTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BankRefundService {
    private static final Logger log = LoggerFactory.getLogger(BankRefundService.class);
    private final AccountRepository accounts;
    private final RefundTransactionRepository refunds;

    public BankRefundService(AccountRepository accounts, RefundTransactionRepository refunds) {
        this.accounts = accounts;
        this.refunds = refunds;
    }

    @Transactional
    public RefundResponse refund(RefundRequest request) {
        var replay = refunds.findByRefundId(request.refundId());
        if (replay.isPresent()) {
            log.info("event=BANK_REFUND_IDEMPOTENT_REPLAY refundId={} transactionId={} status={}",
                request.refundId(), replay.get().getTransactionId(), replay.get().getStatus());
            return response(replay.get());
        }

        var account = accounts.findForUpdate(request.accountNumber()).orElse(null);

        // Close the concurrent duplicate race while a second request was waiting for the account lock.
        replay = refunds.findByRefundId(request.refundId());
        if (replay.isPresent()) return response(replay.get());

        if (account == null) return record(request, "FAILED", "Account not found");
        if (!account.isActive()) return record(request, "FAILED", "Account inactive");

        account.credit(request.amount());
        accounts.save(account);
        var transaction = refunds.save(new RefundTransaction(UUID.randomUUID(), request.refundId(),
            request.originalPaymentId(), request.accountNumber(), request.amount(), "COMPLETED", "Refund credited"));

        log.info("event=BANK_REFUND_COMPLETED refundId={} originalPaymentId={} transactionId={} account={} amount={}",
            request.refundId(), request.originalPaymentId(), transaction.getTransactionId(),
            mask(request.accountNumber()), request.amount());
        return response(transaction);
    }

    private RefundResponse record(RefundRequest request, String status, String message) {
        var tx = refunds.save(new RefundTransaction(UUID.randomUUID(), request.refundId(),
            request.originalPaymentId(), request.accountNumber(), request.amount(), status, message));
        log.warn("event=BANK_REFUND_FAILED refundId={} originalPaymentId={} transactionId={} reason={}",
            request.refundId(), request.originalPaymentId(), tx.getTransactionId(), message);
        return response(tx);
    }

    private RefundResponse response(RefundTransaction tx) {
        return new RefundResponse(tx.getTransactionId(), tx.getStatus(), tx.getMessage());
    }

    private String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}
