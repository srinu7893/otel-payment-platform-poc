package com.srinu.payments.bank.repository;

import com.srinu.payments.bank.domain.RefundTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefundTransactionRepository extends JpaRepository<RefundTransaction, UUID> {
    Optional<RefundTransaction> findByRefundId(UUID refundId);
}
