package com.srinu.payments.payment.api;

import com.srinu.payments.payment.domain.PaymentStatus;
import com.srinu.payments.payment.service.PaymentApplicationService;
import com.srinu.payments.payment.service.RefundApplicationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentApplicationService service;
    private final RefundApplicationService refunds;

    public PaymentController(PaymentApplicationService service, RefundApplicationService refunds) {
        this.service = service;
        this.refunds = refunds;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(customerId(jwt), request));
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(customerId(jwt), id, privileged(jwt));
    }

    @GetMapping
    public Page<PaymentResponse> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) PaymentStatus status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return service.list(customerId(jwt), privileged(jwt), status, page, size);
    }

    @PostMapping("/{id}/cancel")
    public PaymentResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.cancel(customerId(jwt), id, privileged(jwt));
    }

    @PostMapping("/{id}/refunds")
    public ResponseEntity<RefundResponse> refund(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(refunds.create(customerId(jwt), id, request));
    }

    @GetMapping("/{id}/refund")
    public RefundResponse getRefund(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return refunds.get(customerId(jwt), id);
    }

    private String customerId(Jwt jwt) {
        String id = jwt.getClaimAsString("customer_id");
        if (id == null || id.isBlank()) throw new IllegalStateException("JWT missing customer_id claim");
        return id;
    }

    private boolean privileged(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && (roles.contains("SUPPORT") || roles.contains("ADMIN"));
    }
}
