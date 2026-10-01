package com.srinu.payments.bank.repository;

import com.srinu.payments.bank.domain.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {
    Optional<BankTransaction> findByPaymentId(UUID paymentId);
    List<BankTransaction> findBySenderAccountOrderByCreatedAtDesc(String senderAccount);
    List<BankTransaction> findByReceiverAccountOrderByCreatedAtDesc(String receiverAccount);
}
