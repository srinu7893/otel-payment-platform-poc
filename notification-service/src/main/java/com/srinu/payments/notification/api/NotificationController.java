package com.srinu.payments.notification.api;

import com.srinu.payments.notification.domain.NotificationRecord;
import com.srinu.payments.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationRepository repo;

    public NotificationController(NotificationRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public Page<NotificationRecord> list(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(required = false) UUID paymentId,
                                         @RequestParam(required = false) UUID transferId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        if (!privileged(jwt)) {
            return repo.findAllByCustomerId(customerId(jwt), pageable);
        }
        if (paymentId != null) return repo.findAllByPaymentId(paymentId, pageable);
        if (transferId != null) return repo.findAllByTransferId(transferId, pageable);
        return repo.findAll(pageable);
    }

    @GetMapping("/{id}")
    public NotificationRecord get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var record = repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Notification not found"));
        if (!privileged(jwt) && (record.getCustomerId() == null || !record.getCustomerId().equals(customerId(jwt)))) {
            throw new ResponseStatusException(FORBIDDEN, "Notification does not belong to authenticated customer");
        }
        return record;
    }

    private String customerId(Jwt jwt) {
        String id = jwt.getClaimAsString("customer_id");
        if (id == null || id.isBlank()) throw new ResponseStatusException(FORBIDDEN, "JWT missing customer_id claim");
        return id;
    }

    private boolean privileged(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && (roles.contains("SUPPORT") || roles.contains("ADMIN"));
    }
}
