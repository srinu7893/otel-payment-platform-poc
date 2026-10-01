package com.srinu.payments.gateway.api;

import com.srinu.payments.gateway.service.TransferAuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfer-authorizations")
public class TransferAuthorizationController {
    private final TransferAuthorizationService service;

    public TransferAuthorizationController(TransferAuthorizationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransferAuthorizationResponse> authorize(@Valid @RequestBody TransferAuthorizationRequest request) {
        return ResponseEntity.ok(service.authorize(request));
    }
}
