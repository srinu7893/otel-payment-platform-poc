package com.srinu.payments.bank.service;

import com.srinu.payments.bank.api.DebitRequest;
import com.srinu.payments.bank.domain.Account;
import com.srinu.payments.bank.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankTransactionServiceTest {
    @Test
    void debitsWhenBalanceIsSufficient() {
        var repo = mock(AccountRepository.class);
        var account = new Account("ACC1", "Demo", new BigDecimal("100.00"));
        when(repo.findById("ACC1")).thenReturn(Optional.of(account));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var service = new BankTransactionService(repo);
        var result = service.debit(new DebitRequest(UUID.randomUUID(), "ACC1", new BigDecimal("40.00")));
        assertEquals("COMPLETED", result.status());
        assertEquals(new BigDecimal("60.00"), account.getBalance());
    }

    @Test
    void declinesInsufficientFunds() {
        var repo = mock(AccountRepository.class);
        var account = new Account("ACC2", "Demo", new BigDecimal("10.00"));
        when(repo.findById("ACC2")).thenReturn(Optional.of(account));
        var service = new BankTransactionService(repo);
        var result = service.debit(new DebitRequest(UUID.randomUUID(), "ACC2", new BigDecimal("40.00")));
        assertEquals("FAILED", result.status());
        verify(repo, never()).save(any());
    }
}
