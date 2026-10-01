package com.srinu.payments.payment.repository;

import com.srinu.payments.payment.domain.Payment;
import com.srinu.payments.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdempotencyKey(String key);
    Page<Payment> findAllByStatus(PaymentStatus status, Pageable pageable);
    Page<Payment> findAllByStatusAndUpdatedAtBefore(PaymentStatus status, Instant updatedBefore, Pageable pageable);
    Page<Payment> findAllByCustomerId(String customerId, Pageable pageable);
    Page<Payment> findAllByCustomerIdAndStatus(String customerId, PaymentStatus status, Pageable pageable);
}
