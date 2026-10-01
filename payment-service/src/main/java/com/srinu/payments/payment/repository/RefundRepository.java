package com.srinu.payments.payment.repository;

import com.srinu.payments.payment.domain.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    Optional<Refund> findByPaymentId(UUID paymentId);
    Optional<Refund> findByIdempotencyKey(String idempotencyKey);
    Page<Refund> findAllByStatus(String status, Pageable pageable);
    Page<Refund> findAllByStatusAndUpdatedAtBefore(String status, Instant updatedBefore, Pageable pageable);
}
