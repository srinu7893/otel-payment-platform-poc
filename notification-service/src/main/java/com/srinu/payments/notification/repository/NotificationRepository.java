package com.srinu.payments.notification.repository;

import com.srinu.payments.notification.domain.NotificationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationRecord, UUID> {
    Page<NotificationRecord> findAllByPaymentId(UUID paymentId, Pageable pageable);
    Page<NotificationRecord> findAllByTransferId(UUID transferId, Pageable pageable);
    Page<NotificationRecord> findAllByCustomerId(String customerId, Pageable pageable);
    boolean existsBySourceEventIdAndChannel(UUID sourceEventId, String channel);
}
