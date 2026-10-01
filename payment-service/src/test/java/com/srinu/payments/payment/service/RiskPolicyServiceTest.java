package com.srinu.payments.payment.service;

import com.srinu.payments.payment.domain.PaymentStatus;
import com.srinu.payments.payment.repository.PaymentRepository;
import com.srinu.payments.payment.repository.TransferRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RiskPolicyServiceTest {
    @Test
    void rejectsSingleTransactionOverConfiguredLimitWithoutQueryingDailyExposure() {
        var payments = mock(PaymentRepository.class);
        var transfers = mock(TransferRepository.class);
        var service = new RiskPolicyService(payments, transfers,
            new BigDecimal("100.00"), new BigDecimal("500.00"), new BigDecimal("500.00"));

        var error = assertThrows(RiskLimitExceededException.class,
            () -> service.validate("c1", "ACC1", new BigDecimal("100.01")));

        assertEquals("MAX_TRANSACTION_AMOUNT", error.getRule());
        verifyNoInteractions(payments, transfers);
    }

    @Test
    void countsPaymentAndTransferExposureTowardDailyCustomerLimit() {
        var payments = mock(PaymentRepository.class);
        var transfers = mock(TransferRepository.class);
        when(payments.sumExposureByCustomerSince(eq("c1"), any(Instant.class), anyCollection()))
            .thenReturn(new BigDecimal("250.00"));
        when(transfers.sumExposureByCustomerSince(eq("c1"), any(Instant.class), anyCollection()))
            .thenReturn(new BigDecimal("200.00"));

        var service = new RiskPolicyService(payments, transfers,
            new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("1000.00"));

        var error = assertThrows(RiskLimitExceededException.class,
            () -> service.validate("c1", "ACC1", new BigDecimal("60.00")));

        assertEquals("MAX_DAILY_CUSTOMER_AMOUNT", error.getRule());
        verify(payments, never()).sumExposureByAccountSince(anyString(), any(), anyCollection());
    }

    @Test
    void countsPaymentAndTransferExposureTowardDailyAccountLimit() {
        var payments = mock(PaymentRepository.class);
        var transfers = mock(TransferRepository.class);
        when(payments.sumExposureByCustomerSince(eq("c1"), any(Instant.class), anyCollection()))
            .thenReturn(BigDecimal.ZERO);
        when(transfers.sumExposureByCustomerSince(eq("c1"), any(Instant.class), anyCollection()))
            .thenReturn(BigDecimal.ZERO);
        when(payments.sumExposureByAccountSince(eq("ACC1"), any(Instant.class), anyCollection()))
            .thenReturn(new BigDecimal("300.00"));
        when(transfers.sumExposureBySenderSince(eq("ACC1"), any(Instant.class), anyCollection()))
            .thenReturn(new BigDecimal("180.00"));

        var service = new RiskPolicyService(payments, transfers,
            new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("500.00"));

        var error = assertThrows(RiskLimitExceededException.class,
            () -> service.validate("c1", "ACC1", new BigDecimal("25.00")));

        assertEquals("MAX_DAILY_ACCOUNT_AMOUNT", error.getRule());
    }

    @Test
    void allowsTransactionWithinAllLimits() {
        var payments = mock(PaymentRepository.class);
        var transfers = mock(TransferRepository.class);
        when(payments.sumExposureByCustomerSince(anyString(), any(Instant.class), anyCollection()))
            .thenReturn(new BigDecimal("100.00"));
        when(transfers.sumExposureByCustomerSince(anyString(), any(Instant.class), anyCollection()))
            .thenReturn(new BigDecimal("50.00"));
        when(payments.sumExposureByAccountSince(anyString(), any(Instant.class), anyCollection()))
            .thenReturn(new BigDecimal("100.00"));
        when(transfers.sumExposureBySenderSince(anyString(), any(Instant.class), anyCollection()))
            .thenReturn(new BigDecimal("50.00"));

        var service = new RiskPolicyService(payments, transfers,
            new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("500.00"));

        assertDoesNotThrow(() -> service.validate("c1", "ACC1", new BigDecimal("25.00")));
    }
}
