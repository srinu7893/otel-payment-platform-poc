package com.srinu.payments.notification.repository;

import com.srinu.payments.notification.domain.NotificationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationRecord, UUID> {
    Page<NotificationRecord> findAllByPaymentId(UUID paymentId, Pageable pageable);
    Page<NotificationRecord> findAllByTransferId(UUID transferId, Pageable pageable);
    Page<NotificationRecord> findAllByCustomerId(String customerId, Pageable pageable);
    Optional<NotificationRecord> findBySourceEventIdAndChannel(UUID sourceEventId, String channel);
}
