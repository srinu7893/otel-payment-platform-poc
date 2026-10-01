package com.srinu.payments.bank.repository;

import com.srinu.payments.bank.domain.DebitTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DebitTransactionRepository extends JpaRepository<DebitTransaction, UUID> {
    Optional<DebitTransaction> findByPaymentId(UUID paymentId);
}
