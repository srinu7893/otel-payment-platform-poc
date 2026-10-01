package com.srinu.payments.bank.api;

import com.srinu.payments.bank.service.BankTransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bank")
public class BankController {
    private final BankTransactionService service;

    public BankController(BankTransactionService service) {
        this.service = service;
    }

    @PostMapping("/debits")
    public ResponseEntity<DebitResponse> debit(@Valid @RequestBody DebitRequest request) {
        return ResponseEntity.ok(service.debit(request));
    }
}
