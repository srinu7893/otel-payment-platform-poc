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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BankPostgresIntegrationTest {
    @Test @Transactional void onboardingRetryDoesNotResetBalance(){
        String account="ACC"+UUID.randomUUID().toString().replace("-","").substring(0,24).toUpperCase(java.util.Locale.ROOT);
        var controller=new com.srinu.payments.bank.api.OnboardingController(accounts);
        var request=new com.srinu.payments.bank.api.OnboardingController.Request("New customer");
        controller.provision(account,request);assertThat(wallet.balance(account).balance()).isZero();
        wallet.deposit(account,UUID.randomUUID(),new BigDecimal("10.00"));controller.provision(account,request);accounts.flush();
        assertThat(wallet.balance(account).balance()).isEqualByComparingTo("10.00");
    }
    @Autowired private com.srinu.payments.bank.service.WalletService wallet;
    @Test @Transactional void depositsAreIdempotentAndAppearInTheBankLedger() {
        var before=wallet.balance("ACC1001").balance();
        var id=UUID.randomUUID();var amount=new BigDecimal("50.00");
        wallet.deposit("ACC1001",id,amount);wallet.deposit("ACC1001",id,amount);accounts.flush();
        assertThat(wallet.balance("ACC1001").balance()).isEqualByComparingTo(before.add(amount));
        assertThat(wallet.activity("ACC1001",0,100)).anySatisfy(a->{assertThat(a.id()).isEqualTo(id);assertThat(a.direction()).isEqualTo("CREDIT");});
        org.assertj.core.api.Assertions.assertThatThrownBy(()->wallet.deposit("ACC1001",id,new BigDecimal("60.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(wallet.recipient("ACC2001").customerName()).isNotBlank();
    }
    @Test @Transactional void incomingAndOutgoingTransfersAppearOnBothAccountStatements() {
        var response=transferService.transfer(new TransferRequest(UUID.randomUUID(),"ACC1001","ACC2001",new BigDecimal("1.00"),"INR"));
        accounts.flush();
        assertThat(wallet.activity("ACC1001",0,100)).anySatisfy(a->{assertThat(a.id()).isEqualTo(response.transactionId());assertThat(a.direction()).isEqualTo("DEBIT");});
        assertThat(wallet.activity("ACC2001",0,100)).anySatisfy(a->{assertThat(a.id()).isEqualTo(response.transactionId());assertThat(a.direction()).isEqualTo("CREDIT");});
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        if (Boolean.getBoolean("poc.native.integration")) {
            properties.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:55432/poc_integration");
            properties.add("spring.datasource.username", () -> "payments");
            properties.add("spring.datasource.password", () -> "payments");
        } else {
            var postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
            properties.add("spring.datasource.url", postgres::getJdbcUrl);
            properties.add("spring.datasource.username", postgres::getUsername);
            properties.add("spring.datasource.password", postgres::getPassword);
        }
    }

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
