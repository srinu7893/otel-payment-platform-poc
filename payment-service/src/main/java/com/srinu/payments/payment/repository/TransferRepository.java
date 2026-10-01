package com.srinu.payments.payment.repository;

import com.srinu.payments.payment.domain.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
    Page<Transfer> findAllByCustomerId(String customerId, Pageable pageable);
    Page<Transfer> findAllByStatus(String status, Pageable pageable);
    Page<Transfer> findAllByStatusAndUpdatedAtBefore(String status, Instant updatedBefore, Pageable pageable);
}
