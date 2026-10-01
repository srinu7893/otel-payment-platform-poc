package com.srinu.payments.bank.service;

import com.srinu.payments.bank.api.DebitRequest;
import com.srinu.payments.bank.domain.Account;
import com.srinu.payments.bank.domain.DebitTransaction;
import com.srinu.payments.bank.repository.AccountRepository;
import com.srinu.payments.bank.repository.DebitTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankTransactionServiceTest {
    @Test
    void debitsWhenBalanceIsSufficient() {
        var accounts = mock(AccountRepository.class);
        var debits = mock(DebitTransactionRepository.class);
        var account = new Account("ACC1", "Demo", new BigDecimal("100.00"));
        when(debits.findByPaymentId(any())).thenReturn(Optional.empty());
        when(accounts.findForUpdate("ACC1")).thenReturn(Optional.of(account));
        when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));
        when(debits.save(any())).thenAnswer(i -> i.getArgument(0));

        var service = new BankTransactionService(accounts, debits);
        var result = service.debit(new DebitRequest(UUID.randomUUID(), "ACC1", new BigDecimal("40.00")));

        assertEquals("COMPLETED", result.status());
        assertNotNull(result.transactionId());
        assertEquals(new BigDecimal("60.00"), account.getBalance());
        verify(accounts).save(account);
        verify(debits).save(any(DebitTransaction.class));
    }

    @Test
    void declinesInsufficientFundsAndRecordsDecision() {
        var accounts = mock(AccountRepository.class);
        var debits = mock(DebitTransactionRepository.class);
        var account = new Account("ACC2", "Demo", new BigDecimal("10.00"));
        when(debits.findByPaymentId(any())).thenReturn(Optional.empty());
        when(accounts.findForUpdate("ACC2")).thenReturn(Optional.of(account));
        when(debits.save(any())).thenAnswer(i -> i.getArgument(0));

        var service = new BankTransactionService(accounts, debits);
        var result = service.debit(new DebitRequest(UUID.randomUUID(), "ACC2", new BigDecimal("40.00")));

        assertEquals("FAILED", result.status());
        assertNotNull(result.transactionId());
        verify(accounts, never()).save(any());
        verify(debits).save(any(DebitTransaction.class));
    }

    @Test
    void returnsExistingDebitForSamePaymentIdWithoutTouchingBalance() {
        var accounts = mock(AccountRepository.class);
        var debits = mock(DebitTransactionRepository.class);
        UUID paymentId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        var existing = new DebitTransaction(transactionId, paymentId, "ACC1",
            new BigDecimal("25.00"), "COMPLETED", "Debit successful");
        when(debits.findByPaymentId(paymentId)).thenReturn(Optional.of(existing));

        var service = new BankTransactionService(accounts, debits);
        var result = service.debit(new DebitRequest(paymentId, "ACC1", new BigDecimal("25.00")));

        assertEquals("COMPLETED", result.status());
        assertEquals(transactionId, result.transactionId());
        verifyNoInteractions(accounts);
        verify(debits, never()).save(any());
    }
}
