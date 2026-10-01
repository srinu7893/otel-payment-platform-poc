package com.srinu.payments.bank.service;

import com.srinu.payments.bank.api.DebitRequest;
import com.srinu.payments.bank.api.DebitResponse;
import com.srinu.payments.bank.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankTransactionService {
    private final AccountRepository accounts;

    public BankTransactionService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public DebitResponse debit(DebitRequest request) {
        var account = accounts.findById(request.accountNumber())
                .orElse(null);

        if (account == null) {
            return new DebitResponse("FAILED", "Account not found");
        }
        if (!account.hasSufficientBalance(request.amount())) {
            return new DebitResponse("FAILED", "Insufficient balance");
        }

        account.debit(request.amount());
        accounts.save(account);
        return new DebitResponse("COMPLETED", "Debit successful");
    }
}
