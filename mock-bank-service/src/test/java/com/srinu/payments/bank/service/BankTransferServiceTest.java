package com.srinu.payments.bank.service;

import com.srinu.payments.bank.api.TransferRequest;
import com.srinu.payments.bank.domain.Account;
import com.srinu.payments.bank.domain.BankTransaction;
import com.srinu.payments.bank.repository.AccountRepository;
import com.srinu.payments.bank.repository.BankTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BankTransferServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final BankTransactionRepository transactions = mock(BankTransactionRepository.class);
    private final BankTransferService service = new BankTransferService(accounts, transactions);

    @Test
    void transferMovesFundsAndPersistsCompletedLedgerEntry() {
        UUID paymentId = UUID.randomUUID();
        var sender = new Account("ACC1001", "Sender", new BigDecimal("100.00"));
        var receiver = new Account("ACC2001", "Receiver", new BigDecimal("20.00"));
        when(transactions.findByPaymentId(paymentId)).thenReturn(Optional.empty());
        when(accounts.findForUpdate("ACC1001")).thenReturn(Optional.of(sender));
        when(accounts.findForUpdate("ACC2001")).thenReturn(Optional.of(receiver));
        when(transactions.save(any(BankTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.transfer(new TransferRequest(
            paymentId, "ACC1001", "ACC2001", new BigDecimal("10.00"), "INR"));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.transactionId()).isNotNull();
        assertThat(sender.getBalance()).isEqualByComparingTo("90.00");
        assertThat(receiver.getBalance()).isEqualByComparingTo("30.00");
        verify(accounts).save(sender);
        verify(accounts).save(receiver);
        verify(transactions).save(argThat(tx ->
            tx.getPaymentId().equals(paymentId)
                && tx.getStatus().equals("COMPLETED")
                && tx.getType().equals("P2P_TRANSFER")));
    }

    @Test
    void transferRejectsInsufficientFundsWithoutChangingBalances() {
        UUID paymentId = UUID.randomUUID();
        var sender = new Account("ACC1001", "Sender", new BigDecimal("5.00"));
        var receiver = new Account("ACC2001", "Receiver", new BigDecimal("20.00"));
        when(transactions.findByPaymentId(paymentId)).thenReturn(Optional.empty());
        when(accounts.findForUpdate("ACC1001")).thenReturn(Optional.of(sender));
        when(accounts.findForUpdate("ACC2001")).thenReturn(Optional.of(receiver));
        when(transactions.save(any(BankTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.transfer(new TransferRequest(
            paymentId, "ACC1001", "ACC2001", new BigDecimal("10.00"), "INR"));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).isEqualTo("Insufficient balance");
        assertThat(sender.getBalance()).isEqualByComparingTo("5.00");
        assertThat(receiver.getBalance()).isEqualByComparingTo("20.00");
        verify(accounts, never()).save(any());
        verify(transactions).save(argThat(tx -> tx.getStatus().equals("FAILED")));
    }

    @Test
    void transferIsIdempotentForPreviouslyProcessedPaymentId() {
        UUID paymentId = UUID.randomUUID();
        var existing = new BankTransaction(paymentId, "P2P_TRANSFER", "ACC1001", "ACC2001",
            new BigDecimal("10.00"), "INR", "COMPLETED");
        when(transactions.findByPaymentId(paymentId)).thenReturn(Optional.of(existing));

        var response = service.transfer(new TransferRequest(
            paymentId, "ACC1001", "ACC2001", new BigDecimal("10.00"), "INR"));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.transactionId()).isEqualTo(existing.getId());
        verifyNoInteractions(accounts);
        verify(transactions, never()).save(any());
    }
}
