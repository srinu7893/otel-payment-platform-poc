package com.srinu.payments.payment.api;

import com.srinu.payments.payment.service.TransferApplicationService;
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
@RequestMapping("/api/v1/transfers")
public class TransferController {
    private final TransferApplicationService service;

    public TransferController(TransferApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(customerId(jwt), request));
    }

    @GetMapping("/{id}")
    public TransferResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(customerId(jwt), id, privileged(jwt));
    }

    @GetMapping
    public Page<TransferResponse> list(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.list(customerId(jwt), privileged(jwt), page, size);
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
