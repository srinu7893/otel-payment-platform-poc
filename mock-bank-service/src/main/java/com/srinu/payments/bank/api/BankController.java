package com.srinu.payments.bank.api;

import com.srinu.payments.bank.service.BankRefundService;
import com.srinu.payments.bank.service.BankTransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bank")
public class BankController {
    private final BankTransactionService transactions;
    private final BankRefundService refunds;

    public BankController(BankTransactionService transactions, BankRefundService refunds) {
        this.transactions = transactions;
        this.refunds = refunds;
    }

    @PostMapping("/debits")
    public ResponseEntity<DebitResponse> debit(@Valid @RequestBody DebitRequest request) {
        return ResponseEntity.ok(transactions.debit(request));
    }

    @PostMapping("/refunds")
    public ResponseEntity<RefundResponse> refund(@Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(refunds.refund(request));
    }
}
