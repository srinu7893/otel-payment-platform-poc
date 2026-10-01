package com.srinu.payments.payment.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e " +
           "where e.status = 'PENDING' and e.nextAttemptAt <= :now " +
           "order by e.createdAt asc")
    List<OutboxEvent> lockPending(@Param("now") Instant now, Pageable pageable);
}
