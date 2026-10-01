package com.srinu.payments.bank.service;

import com.srinu.payments.bank.api.TransferRequest;
import com.srinu.payments.bank.api.TransferResponse;
import com.srinu.payments.bank.domain.BankTransaction;
import com.srinu.payments.bank.repository.AccountRepository;
import com.srinu.payments.bank.repository.BankTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankTransferService {
    private static final Logger log = LoggerFactory.getLogger(BankTransferService.class);
    private final AccountRepository accounts;
    private final BankTransactionRepository transactions;

    public BankTransferService(AccountRepository accounts, BankTransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        var prior = transactions.findByPaymentId(request.paymentId());
        if (prior.isPresent()) {
            var tx = prior.get();
            log.info("event=BANK_TRANSFER_IDEMPOTENT_REPLAY paymentId={} transactionId={}", request.paymentId(), tx.getId());
            return new TransferResponse(tx.getId(), tx.getStatus(), "Transfer already processed");
        }
        if (request.senderAccount().equals(request.receiverAccount())) {
            return failure(request, "Sender and receiver accounts must be different");
        }

        // Lock in deterministic order to reduce deadlock risk when two accounts are involved.
        String firstKey = request.senderAccount().compareTo(request.receiverAccount()) < 0 ? request.senderAccount() : request.receiverAccount();
        String secondKey = firstKey.equals(request.senderAccount()) ? request.receiverAccount() : request.senderAccount();
        var first = accounts.findForUpdate(firstKey).orElse(null);
        var second = accounts.findForUpdate(secondKey).orElse(null);
        if (first == null || second == null) return failure(request, "Sender or receiver account not found");

        var sender = request.senderAccount().equals(first.getAccountNumber()) ? first : second;
        var receiver = request.receiverAccount().equals(first.getAccountNumber()) ? first : second;
        if (!sender.isActive() || !receiver.isActive()) return failure(request, "Sender or receiver account is inactive");
        if (!sender.hasSufficientBalance(request.amount())) return failure(request, "Insufficient balance");

        log.info("event=BANK_TRANSFER_STARTED paymentId={} sender={} receiver={} amount={} currency={}",
            request.paymentId(), mask(sender.getAccountNumber()), mask(receiver.getAccountNumber()), request.amount(), request.currency());

        sender.debit(request.amount());
        receiver.credit(request.amount());
        accounts.save(sender);
        accounts.save(receiver);

        var tx = transactions.save(new BankTransaction(request.paymentId(), "P2P_TRANSFER",
            request.senderAccount(), request.receiverAccount(), request.amount(), request.currency(), "COMPLETED"));
        log.info("event=BANK_TRANSFER_COMPLETED paymentId={} transactionId={} amount={} currency={}",
            request.paymentId(), tx.getId(), request.amount(), request.currency());
        return new TransferResponse(tx.getId(), "COMPLETED", "Transfer completed");
    }

    private TransferResponse failure(TransferRequest request, String message) {
        var tx = transactions.save(new BankTransaction(request.paymentId(), "P2P_TRANSFER",
            request.senderAccount(), request.receiverAccount(), request.amount(), request.currency(), "FAILED"));
        log.warn("event=BANK_TRANSFER_FAILED paymentId={} transactionId={} reason={}", request.paymentId(), tx.getId(), message);
        return new TransferResponse(tx.getId(), "FAILED", message);
    }

    private String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}
