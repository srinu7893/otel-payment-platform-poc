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
            seed(accounts, "ACC1001", "Demo Customer", "5000.00");
            seed(accounts, "ACC1002", "Low Balance Customer", "25.00");
            seed(accounts, "ACC-SLOW", "Slow Scenario Customer", "5000.00");
            seed(accounts, "ACC-ERROR", "Failure Scenario Customer", "5000.00");
        };
    }

    private void seed(AccountRepository accounts, String number, String name, String balance) {
        if (!accounts.existsById(number)) {
            accounts.save(new Account(number, name, new BigDecimal(balance)));
        }
    }
}
