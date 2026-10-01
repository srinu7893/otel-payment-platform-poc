package com.srinu.payments.bank.integration;

import com.srinu.payments.bank.api.DebitRequest;
import com.srinu.payments.bank.api.RefundRequest;
import com.srinu.payments.bank.api.TransferRequest;
import com.srinu.payments.bank.repository.AccountRepository;
import com.srinu.payments.bank.service.BankRefundService;
import com.srinu.payments.bank.service.BankTransactionService;
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

    @Autowired
    private BankTransactionService debitService;

    @Autowired
    private BankRefundService refundService;

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

    @Test
    @Transactional
    void repeatedMerchantDebitWithSamePaymentIdChangesBalanceOnlyOnce() {
        UUID paymentId = UUID.randomUUID();
        var amount = new BigDecimal("25.00");
        var before = accounts.findById("ACC1001").orElseThrow().getBalance();

        var first = debitService.debit(new DebitRequest(paymentId, "ACC1001", amount));
        var second = debitService.debit(new DebitRequest(paymentId, "ACC1001", amount));

        accounts.flush();
        var after = accounts.findById("ACC1001").orElseThrow().getBalance();

        assertThat(first.status()).isEqualTo("COMPLETED");
        assertThat(second.status()).isEqualTo("COMPLETED");
        assertThat(second.transactionId()).isEqualTo(first.transactionId());
        assertThat(after).isEqualByComparingTo(before.subtract(amount));
    }

    @Test
    @Transactional
    void repeatedRefundWithSameRefundIdCreditsBalanceOnlyOnce() {
        UUID refundId = UUID.randomUUID();
        UUID originalPaymentId = UUID.randomUUID();
        var amount = new BigDecimal("30.00");
        var before = accounts.findById("ACC1001").orElseThrow().getBalance();

        var first = refundService.refund(new RefundRequest(refundId, originalPaymentId, "ACC1001", amount));
        var second = refundService.refund(new RefundRequest(refundId, originalPaymentId, "ACC1001", amount));

        accounts.flush();
        var after = accounts.findById("ACC1001").orElseThrow().getBalance();

        assertThat(first.status()).isEqualTo("COMPLETED");
        assertThat(second.status()).isEqualTo("COMPLETED");
        assertThat(second.transactionId()).isEqualTo(first.transactionId());
        assertThat(after).isEqualByComparingTo(before.add(amount));
    }
}
