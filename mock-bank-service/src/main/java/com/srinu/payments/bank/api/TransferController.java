package com.srinu.payments.bank.api;

import com.srinu.payments.bank.service.BankTransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {
    private final BankTransferService service;

    public TransferController(BankTransferService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        var response = service.transfer(request);
        return ResponseEntity.ok(response);
    }
}
