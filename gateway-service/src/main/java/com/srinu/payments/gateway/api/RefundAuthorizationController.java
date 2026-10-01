package com.srinu.payments.gateway.api;

import com.srinu.payments.gateway.service.RefundAuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/refund-authorizations")
public class RefundAuthorizationController {
    private final RefundAuthorizationService service;

    public RefundAuthorizationController(RefundAuthorizationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RefundAuthorizationResponse> authorize(@Valid @RequestBody RefundAuthorizationRequest request) {
        return ResponseEntity.ok(service.authorize(request));
    }
}
