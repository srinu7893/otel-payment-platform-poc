package com.srinu.payments.bank.service;

import com.srinu.payments.bank.api.DebitRequest;
import com.srinu.payments.bank.api.DebitResponse;
import com.srinu.payments.bank.domain.DebitTransaction;
import com.srinu.payments.bank.repository.AccountRepository;
import com.srinu.payments.bank.repository.DebitTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BankTransactionService {
    private static final Logger log = LoggerFactory.getLogger(BankTransactionService.class);
    private final AccountRepository accounts;
    private final DebitTransactionRepository debits;

    public BankTransactionService(AccountRepository accounts, DebitTransactionRepository debits) {
        this.accounts = accounts;
        this.debits = debits;
    }

    @Transactional
    public DebitResponse debit(DebitRequest request) {
        long started = System.currentTimeMillis();
        String maskedAccount = mask(request.accountNumber());

        var replay = debits.findByPaymentId(request.paymentId());
        if (replay.isPresent()) {
            log.info("event=BANK_DEBIT_IDEMPOTENT_REPLAY paymentId={} transactionId={} status={}",
                request.paymentId(), replay.get().getTransactionId(), replay.get().getStatus());
            return response(replay.get());
        }

        log.info("event=BANK_DEBIT_STARTED paymentId={} account={} amount={}",
            request.paymentId(), maskedAccount, request.amount());

        // Deterministic POC failure accounts let us reproduce production-style incidents.
        if ("ACC-SLOW".equalsIgnoreCase(request.accountNumber())) {
            sleep(5000);
        }
        if ("ACC-ERROR".equalsIgnoreCase(request.accountNumber())) {
            log.error("event=BANK_SIMULATED_FAILURE paymentId={} account={}", request.paymentId(), maskedAccount);
            throw new IllegalStateException("Simulated bank processing failure");
        }

        var account = accounts.findForUpdate(request.accountNumber()).orElse(null);

        // A concurrent request with the same paymentId may have completed while this request waited on the row lock.
        replay = debits.findByPaymentId(request.paymentId());
        if (replay.isPresent()) {
            log.info("event=BANK_DEBIT_CONCURRENT_REPLAY paymentId={} transactionId={} status={}",
                request.paymentId(), replay.get().getTransactionId(), replay.get().getStatus());
            return response(replay.get());
        }

        if (account == null) {
            return record(request, "FAILED", "Account not found", maskedAccount, started);
        }
        if (!account.isActive()) {
            return record(request, "FAILED", "Account inactive", maskedAccount, started);
        }
        if (!account.hasSufficientBalance(request.amount())) {
            return record(request, "FAILED", "Insufficient balance", maskedAccount, started);
        }

        account.debit(request.amount());
        accounts.save(account);
        return record(request, "COMPLETED", "Debit successful", maskedAccount, started);
    }

    private DebitResponse record(DebitRequest request, String status, String message,
                                 String maskedAccount, long started) {
        var transaction = debits.save(new DebitTransaction(
            UUID.randomUUID(), request.paymentId(), request.accountNumber(), request.amount(), status, message));
        if ("COMPLETED".equals(status)) {
            log.info("event=BANK_DEBIT_COMPLETED paymentId={} transactionId={} account={} durationMs={}",
                request.paymentId(), transaction.getTransactionId(), maskedAccount,
                System.currentTimeMillis() - started);
        } else {
            log.warn("event=BANK_DEBIT_DECLINED paymentId={} transactionId={} account={} reason={} durationMs={}",
                request.paymentId(), transaction.getTransactionId(), maskedAccount, message,
                System.currentTimeMillis() - started);
        }
        return response(transaction);
    }

    private DebitResponse response(DebitTransaction transaction) {
        return new DebitResponse(transaction.getTransactionId(), transaction.getStatus(), transaction.getMessage());
    }

    private static String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bank processing interrupted", e);
        }
    }
}
