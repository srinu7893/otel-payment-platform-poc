package com.srinu.payments.notification.api;

import com.srinu.payments.notification.domain.NotificationRecord;
import com.srinu.payments.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationRepository repo;
    public NotificationController(NotificationRepository repo){ this.repo=repo; }

    @GetMapping
    public Page<NotificationRecord> list(@RequestParam(required=false) UUID paymentId,
                                         @RequestParam(defaultValue="0") int page,
                                         @RequestParam(defaultValue="20") int size){
        var pageable=PageRequest.of(page, Math.min(size,100));
        return paymentId==null ? repo.findAll(pageable) : repo.findAllByPaymentId(paymentId,pageable);
    }

    @GetMapping("/{id}")
    public NotificationRecord get(@PathVariable UUID id){
        return repo.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Notification not found"));
    }
}
