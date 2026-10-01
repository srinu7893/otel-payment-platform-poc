package com.srinu.payments.gateway.service;

import com.srinu.payments.gateway.api.AuthorizationRequest;
import com.srinu.payments.gateway.client.BankClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationServiceTest {
    @Test
    void returnsCompletedWhenBankApproves() {
        var bank = mock(BankClient.class);
        var paymentId = UUID.randomUUID();
        when(bank.debit(paymentId, "ACC1001", new BigDecimal("50.00")))
                .thenReturn(new BankClient.BankDebitResponse("COMPLETED", "Debit successful"));

        var service = new AuthorizationService(bank);
        var result = service.authorize(new AuthorizationRequest(paymentId, "ACC1001", new BigDecimal("50.00")));

        assertEquals("COMPLETED", result.status());
    }

    @Test
    void returnsFailedWhenBankClientThrows() {
        var bank = mock(BankClient.class);
        var paymentId = UUID.randomUUID();
        when(bank.debit(any(), anyString(), any())).thenThrow(new RuntimeException("downstream unavailable"));

        var service = new AuthorizationService(bank);
        var result = service.authorize(new AuthorizationRequest(paymentId, "ACC1001", new BigDecimal("50.00")));

        assertEquals("FAILED", result.status());
    }
}
