package com.srinu.payments.bank.config;

import com.srinu.payments.bank.domain.Account;
import com.srinu.payments.bank.repository.AccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class DemoDataConfig {
    @Bean
    CommandLineRunner seedAccounts(AccountRepository accounts) {
        return args -> {
            if (!accounts.existsById("ACC1001")) {
                accounts.save(new Account("ACC1001", "Demo Customer", new BigDecimal("5000.00")));
            }
            if (!accounts.existsById("ACC1002")) {
                accounts.save(new Account("ACC1002", "Low Balance Customer", new BigDecimal("25.00")));
            }
        };
    }
}
