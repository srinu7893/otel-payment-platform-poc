package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.PaymentRequest;
import com.srinu.payments.payment.client.CustomerClient;
import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Payment;
import com.srinu.payments.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentApplicationServiceTest {
    @Test
    void completesApprovedPaymentForOwnedAccount() {
        var repo = mock(PaymentRepository.class);
        var customers = mock(CustomerClient.class);
        var gateway = mock(GatewayClient.class);
        var state = mock(PaymentStateService.class);
        var risk = mock(RiskPolicyService.class);
        var paymentId = UUID.randomUUID();
        var bankTransactionId = UUID.randomUUID();
        var request = new PaymentRequest("k1", "ACC1001", "Demo Store", new BigDecimal("50.00"));

        when(state.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(customers.get("demo-customer"))
            .thenReturn(new CustomerClient.CustomerView("demo-customer", "Demo", "demo@example.com", "ACC1001", true));

        var processing = new Payment(paymentId, "k1", "demo-customer", "ACC1001", "Demo Store", new BigDecimal("50.00"));
        processing.markProcessing();
        when(state.createProcessing("demo-customer", request)).thenReturn(processing);

        var gatewayResult = new GatewayClient.GatewayResult(bankTransactionId, "COMPLETED", "Approved");
        when(gateway.authorize(paymentId, "ACC1001", new BigDecimal("50.00"))).thenReturn(gatewayResult);

        var completed = new Payment(paymentId, "k1", "demo-customer", "ACC1001", "Demo Store", new BigDecimal("50.00"));
        completed.markProcessing();
        completed.markCompleted(bankTransactionId);
        when(state.applyGatewayResult(paymentId, gatewayResult)).thenReturn(completed);

        var service = new PaymentApplicationService(repo, customers, gateway, state, risk);
        var response = service.create("demo-customer", request);

        assertEquals("COMPLETED", response.status());
        assertEquals("Demo Store", response.merchant());
        verify(risk).validate("demo-customer", "ACC1001", new BigDecimal("50.00"));
        verify(state).createProcessing("demo-customer", request);
        verify(state).applyGatewayResult(paymentId, gatewayResult);
    }

    @Test
    void rejectsPaymentFromAccountNotOwnedByCustomerBeforeCreatingPayment() {
        var repo = mock(PaymentRepository.class);
        var customers = mock(CustomerClient.class);
        var gateway = mock(GatewayClient.class);
        var state = mock(PaymentStateService.class);
        var risk = mock(RiskPolicyService.class);
        when(state.findByIdempotencyKey("k2")).thenReturn(Optional.empty());
        when(customers.get("demo-customer"))
            .thenReturn(new CustomerClient.CustomerView("demo-customer", "Demo", "demo@example.com", "ACC1001", true));

        var service = new PaymentApplicationService(repo, customers, gateway, state, risk);

        assertThrows(PaymentApplicationService.PaymentAuthorizationException.class,
            () -> service.create("demo-customer",
                new PaymentRequest("k2", "ACC2001", "Demo Store", new BigDecimal("25.00"))));
        verifyNoInteractions(gateway, risk);
        verify(state, never()).createProcessing(anyString(), any());
    }

    @Test
    void rejectsRiskLimitBeforeCreatingProcessingStateOrCallingGateway() {
        var repo = mock(PaymentRepository.class);
        var customers = mock(CustomerClient.class);
        var gateway = mock(GatewayClient.class);
        var state = mock(PaymentStateService.class);
        var risk = mock(RiskPolicyService.class);
        var request = new PaymentRequest("risk-key", "ACC1001", "Demo Store", new BigDecimal("100001.00"));

        when(state.findByIdempotencyKey("risk-key")).thenReturn(Optional.empty());
        when(customers.get("demo-customer"))
            .thenReturn(new CustomerClient.CustomerView("demo-customer", "Demo", "demo@example.com", "ACC1001", true));
        doThrow(new RiskLimitExceededException("MAX_TRANSACTION_AMOUNT", "limit exceeded"))
            .when(risk).validate("demo-customer", "ACC1001", new BigDecimal("100001.00"));

        var service = new PaymentApplicationService(repo, customers, gateway, state, risk);

        var error = assertThrows(RiskLimitExceededException.class,
            () -> service.create("demo-customer", request));
        assertEquals("MAX_TRANSACTION_AMOUNT", error.getRule());
        verify(state, never()).createProcessing(anyString(), any());
        verifyNoInteractions(gateway);
    }

    @Test
    void marksReconciliationRequiredWhenGatewayThrowsAfterProcessingStateExists() {
        var repo = mock(PaymentRepository.class);
        var customers = mock(CustomerClient.class);
        var gateway = mock(GatewayClient.class);
        var state = mock(PaymentStateService.class);
        var risk = mock(RiskPolicyService.class);
        var paymentId = UUID.randomUUID();
        var request = new PaymentRequest("k3", "ACC1001", "Demo Store", new BigDecimal("20.00"));

        when(state.findByIdempotencyKey("k3")).thenReturn(Optional.empty());
        when(customers.get("demo-customer"))
            .thenReturn(new CustomerClient.CustomerView("demo-customer", "Demo", "demo@example.com", "ACC1001", true));

        var processing = new Payment(paymentId, "k3", "demo-customer", "ACC1001", "Demo Store", new BigDecimal("20.00"));
        processing.markProcessing();
        when(state.createProcessing("demo-customer", request)).thenReturn(processing);
        when(gateway.authorize(paymentId, "ACC1001", new BigDecimal("20.00")))
            .thenThrow(new RuntimeException("timeout"));

        var unknown = new Payment(paymentId, "k3", "demo-customer", "ACC1001", "Demo Store", new BigDecimal("20.00"));
        unknown.markProcessing();
        unknown.markReconciliationRequired("DOWNSTREAM_OUTCOME_UNKNOWN");
        when(state.markReconciliationRequired(paymentId, "DOWNSTREAM_OUTCOME_UNKNOWN")).thenReturn(unknown);

        var service = new PaymentApplicationService(repo, customers, gateway, state, risk);
        var response = service.create("demo-customer", request);

        assertEquals("RECONCILIATION_REQUIRED", response.status());
        verify(state).markReconciliationRequired(paymentId, "DOWNSTREAM_OUTCOME_UNKNOWN");
    }
}
