package com.srinu.payments.payment.repository;

import com.srinu.payments.payment.domain.Transfer;
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

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
    Page<Transfer> findAllByCustomerId(String customerId, Pageable pageable);
    Page<Transfer> findAllByStatus(String status, Pageable pageable);
    Page<Transfer> findAllByStatusAndUpdatedAtBefore(String status, Instant updatedBefore, Pageable pageable);

    @Query("select coalesce(sum(t.amount), 0) from Transfer t " +
        "where t.customerId = :customerId and t.createdAt >= :since and t.status in :statuses")
    BigDecimal sumExposureByCustomerSince(@Param("customerId") String customerId,
                                          @Param("since") Instant since,
                                          @Param("statuses") Collection<String> statuses);

    @Query("select coalesce(sum(t.amount), 0) from Transfer t " +
        "where t.senderAccount = :accountNumber and t.createdAt >= :since and t.status in :statuses")
    BigDecimal sumExposureBySenderSince(@Param("accountNumber") String accountNumber,
                                        @Param("since") Instant since,
                                        @Param("statuses") Collection<String> statuses);
}
