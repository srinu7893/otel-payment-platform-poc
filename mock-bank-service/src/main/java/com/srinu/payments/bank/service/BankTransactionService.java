package com.srinu.payments.bank.service;

import com.srinu.payments.bank.api.DebitRequest;
import com.srinu.payments.bank.api.DebitResponse;
import com.srinu.payments.bank.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankTransactionService {
    private static final Logger log = LoggerFactory.getLogger(BankTransactionService.class);
    private final AccountRepository accounts;

    public BankTransactionService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public DebitResponse debit(DebitRequest request) {
        long started = System.currentTimeMillis();
        String maskedAccount = mask(request.accountNumber());
        log.info("event=BANK_DEBIT_STARTED paymentId={} account={} amount={}", request.paymentId(), maskedAccount, request.amount());

        // Deterministic POC failure accounts let us reproduce production-style incidents.
        if ("ACC-SLOW".equalsIgnoreCase(request.accountNumber())) {
            sleep(5000);
        }
        if ("ACC-ERROR".equalsIgnoreCase(request.accountNumber())) {
            log.error("event=BANK_SIMULATED_FAILURE paymentId={} account={}", request.paymentId(), maskedAccount);
            throw new IllegalStateException("Simulated bank processing failure");
        }

        var account = accounts.findById(request.accountNumber()).orElse(null);
        if (account == null) {
            log.warn("event=BANK_DEBIT_DECLINED reason=ACCOUNT_NOT_FOUND paymentId={} account={}", request.paymentId(), maskedAccount);
            return new DebitResponse("FAILED", "Account not found");
        }
        if (!account.isActive()) {
            log.warn("event=BANK_DEBIT_DECLINED reason=ACCOUNT_INACTIVE paymentId={} account={}", request.paymentId(), maskedAccount);
            return new DebitResponse("FAILED", "Account inactive");
        }
        if (!account.hasSufficientBalance(request.amount())) {
            log.warn("event=BANK_DEBIT_DECLINED reason=INSUFFICIENT_FUNDS paymentId={} account={} amount={}", request.paymentId(), maskedAccount, request.amount());
            return new DebitResponse("FAILED", "Insufficient balance");
        }

        account.debit(request.amount());
        accounts.save(account);
        log.info("event=BANK_DEBIT_COMPLETED paymentId={} account={} durationMs={}", request.paymentId(), maskedAccount, System.currentTimeMillis()-started);
        return new DebitResponse("COMPLETED", "Debit successful");
    }

    private static String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Bank processing interrupted", e); }
    }
}
