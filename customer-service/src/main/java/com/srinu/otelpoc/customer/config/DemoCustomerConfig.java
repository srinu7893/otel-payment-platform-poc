package com.srinu.otelpoc.customer.config;

import com.srinu.otelpoc.customer.domain.Customer;
import com.srinu.otelpoc.customer.repository.CustomerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoCustomerConfig {
    @Bean
    CommandLineRunner seed(CustomerRepository repo) {
        return args -> {
            if (!repo.existsById("demo-customer")) {
                repo.save(new Customer("demo-customer", "Demo User", "demo@example.com", "ACC1001"));
            }
            if (!repo.existsById("receiver-customer")) {
                repo.save(new Customer("receiver-customer", "Receiver User", "receiver@example.com", "ACC2001"));
            }
            if (!repo.existsById("slow-customer")) {
                repo.save(new Customer("slow-customer", "Slow Bank Demo", "slow@example.com", "ACC-SLOW"));
            }
            if (!repo.existsById("error-customer")) {
                repo.save(new Customer("error-customer", "Error Bank Demo", "error@example.com", "ACC-ERROR"));
            }
        };
    }
}
