package com.srinu.payments.payment.repository;

import com.srinu.payments.payment.domain.Payment;
import com.srinu.payments.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdempotencyKey(String key);
    Page<Payment> findAllByStatus(PaymentStatus status, Pageable pageable);
    Page<Payment> findAllByStatusAndUpdatedAtBefore(PaymentStatus status, Instant updatedBefore, Pageable pageable);
    Page<Payment> findAllByCustomerId(String customerId, Pageable pageable);
    Page<Payment> findAllByCustomerIdAndStatus(String customerId, PaymentStatus status, Pageable pageable);

    @Query("select coalesce(sum(p.amount), 0) from Payment p " +
        "where p.customerId = :customerId and p.createdAt >= :since and p.status in :statuses")
    BigDecimal sumExposureByCustomerSince(@Param("customerId") String customerId,
                                          @Param("since") Instant since,
                                          @Param("statuses") Collection<PaymentStatus> statuses);

    @Query("select coalesce(sum(p.amount), 0) from Payment p " +
        "where p.accountNumber = :accountNumber and p.createdAt >= :since and p.status in :statuses")
    BigDecimal sumExposureByAccountSince(@Param("accountNumber") String accountNumber,
                                         @Param("since") Instant since,
                                         @Param("statuses") Collection<PaymentStatus> statuses);
}
