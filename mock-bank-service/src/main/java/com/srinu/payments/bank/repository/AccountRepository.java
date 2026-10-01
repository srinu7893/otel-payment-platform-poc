package com.srinu.payments.bank.repository;

import com.srinu.payments.bank.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
}
