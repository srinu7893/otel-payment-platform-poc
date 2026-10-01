package com.srinu.payments.bank.integration;

import com.srinu.payments.bank.api.TransferRequest;
import com.srinu.payments.bank.repository.AccountRepository;
import com.srinu.payments.bank.service.BankTransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class BankPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private BankTransferService transferService;

    @Test
    @Transactional
    void flywaySchemaAndLockedTransferWorkOnRealPostgres() {
        var senderBefore = accounts.findById("ACC1001").orElseThrow().getBalance();
        var receiverBefore = accounts.findById("ACC2001").orElseThrow().getBalance();
        var amount = new BigDecimal("100.00");

        var response = transferService.transfer(new TransferRequest(
            UUID.randomUUID(), "ACC1001", "ACC2001", amount, "INR"));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.transactionId()).isNotNull();

        accounts.flush();
        var senderAfter = accounts.findById("ACC1001").orElseThrow().getBalance();
        var receiverAfter = accounts.findById("ACC2001").orElseThrow().getBalance();

        assertThat(senderAfter).isEqualByComparingTo(senderBefore.subtract(amount));
        assertThat(receiverAfter).isEqualByComparingTo(receiverBefore.add(amount));
    }
}
