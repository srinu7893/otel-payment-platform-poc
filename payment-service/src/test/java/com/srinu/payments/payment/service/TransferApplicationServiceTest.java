package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.TransferRequest;
import com.srinu.payments.payment.client.CustomerClient;
import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Transfer;
import com.srinu.payments.payment.repository.TransferRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransferApplicationServiceTest {

    @Test
    void completesTransferWhenAuthenticatedCustomerOwnsSenderAccount() {
        var repo = mock(TransferRepository.class);
        var customers = mock(CustomerClient.class);
        var gateway = mock(GatewayClient.class);
        var state = mock(TransferStateService.class);
        var risk = mock(RiskPolicyService.class);
        var transferId = UUID.randomUUID();
        var bankTx = UUID.randomUUID();
        var request = new TransferRequest("idem-1", "ACC1001", "ACC2001", new BigDecimal("125.00"), "INR");

        when(state.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(customers.get("demo-customer"))
            .thenReturn(new CustomerClient.CustomerView("demo-customer", "Demo", "demo@example.com", "ACC1001", true));

        var processing = new Transfer(transferId, "idem-1", "demo-customer", "ACC1001", "ACC2001",
            new BigDecimal("125.00"), "INR");
        processing.markProcessing();
        when(state.createProcessing("demo-customer", request)).thenReturn(processing);

        var gatewayResult = new GatewayClient.TransferGatewayResult(bankTx, "COMPLETED", "Transfer completed");
        when(gateway.transfer(transferId, "ACC1001", "ACC2001", new BigDecimal("125.00"), "INR"))
            .thenReturn(gatewayResult);

        var completed = new Transfer(transferId, "idem-1", "demo-customer", "ACC1001", "ACC2001",
            new BigDecimal("125.00"), "INR");
        completed.markProcessing();
        completed.complete(bankTx);
        when(state.applyGatewayResult(transferId, gatewayResult)).thenReturn(completed);

        var service = new TransferApplicationService(repo, customers, gateway, state, risk);
        var result = service.create("demo-customer", request);

        assertEquals("COMPLETED", result.status());
        assertEquals(bankTx, result.bankTransactionId());
        assertEquals("****1001", result.senderAccount());
        assertEquals("****2001", result.receiverAccount());
        verify(risk).validate("demo-customer", "ACC1001", new BigDecimal("125.00"));
        verify(state).applyGatewayResult(transferId, gatewayResult);
    }

    @Test
    void rejectsSenderAccountThatDoesNotBelongToAuthenticatedCustomer() {
        var repo = mock(TransferRepository.class);
        var customers = mock(CustomerClient.class);
        var gateway = mock(GatewayClient.class);
        var state = mock(TransferStateService.class);
        var risk = mock(RiskPolicyService.class);

        when(state.findByIdempotencyKey("idem-2")).thenReturn(Optional.empty());
        when(customers.get("demo-customer"))
            .thenReturn(new CustomerClient.CustomerView("demo-customer", "Demo", "demo@example.com", "ACC1001", true));

        var service = new TransferApplicationService(repo, customers, gateway, state, risk);

        assertThrows(TransferApplicationService.TransferAuthorizationException.class,
            () -> service.create("demo-customer",
                new TransferRequest("idem-2", "ACC9999", "ACC2001", new BigDecimal("25.00"), "INR")));
        verifyNoInteractions(gateway, risk);
        verify(state, never()).createProcessing(anyString(), any());
    }

    @Test
    void returnsPriorTransferForIdempotentReplayWithoutRecheckingRisk() {
        var repo = mock(TransferRepository.class);
        var customers = mock(CustomerClient.class);
        var gateway = mock(GatewayClient.class);
        var state = mock(TransferStateService.class);
        var risk = mock(RiskPolicyService.class);

        var existing = new Transfer(UUID.randomUUID(), "same-key", "demo-customer", "ACC1001", "ACC2001",
            new BigDecimal("10.00"), "INR");
        existing.markProcessing();
        when(state.findByIdempotencyKey("same-key")).thenReturn(Optional.of(existing));

        var service = new TransferApplicationService(repo, customers, gateway, state, risk);
        var result = service.create("demo-customer",
            new TransferRequest("same-key", "ACC1001", "ACC2001", new BigDecimal("10.00"), "INR"));

        assertEquals(existing.getId(), result.transferId());
        assertEquals("PROCESSING", result.status());
        verifyNoInteractions(customers, gateway, risk);
    }

    @Test
    void rejectsRiskLimitBeforeCreatingProcessingStateOrCallingGateway() {
        var repo = mock(TransferRepository.class);
        var customers = mock(CustomerClient.class);
        var gateway = mock(GatewayClient.class);
        var state = mock(TransferStateService.class);
        var risk = mock(RiskPolicyService.class);
        var request = new TransferRequest("risk-transfer", "ACC1001", "ACC2001",
            new BigDecimal("100001.00"), "INR");

        when(state.findByIdempotencyKey("risk-transfer")).thenReturn(Optional.empty());
        when(customers.get("demo-customer"))
            .thenReturn(new CustomerClient.CustomerView("demo-customer", "Demo", "demo@example.com", "ACC1001", true));
        doThrow(new RiskLimitExceededException("MAX_TRANSACTION_AMOUNT", "limit exceeded"))
            .when(risk).validate("demo-customer", "ACC1001", new BigDecimal("100001.00"));

        var service = new TransferApplicationService(repo, customers, gateway, state, risk);

        assertThrows(RiskLimitExceededException.class, () -> service.create("demo-customer", request));
        verify(state, never()).createProcessing(anyString(), any());
        verifyNoInteractions(gateway);
    }

    @Test
    void marksTransferForReconciliationWhenGatewayThrows() {
        var repo = mock(TransferRepository.class);
        var customers = mock(CustomerClient.class);
        var gateway = mock(GatewayClient.class);
        var state = mock(TransferStateService.class);
        var risk = mock(RiskPolicyService.class);
        var transferId = UUID.randomUUID();
        var request = new TransferRequest("idem-3", "ACC1001", "ACC2001", new BigDecimal("15.00"), "INR");

        when(state.findByIdempotencyKey("idem-3")).thenReturn(Optional.empty());
        when(customers.get("demo-customer"))
            .thenReturn(new CustomerClient.CustomerView("demo-customer", "Demo", "demo@example.com", "ACC1001", true));

        var processing = new Transfer(transferId, "idem-3", "demo-customer", "ACC1001", "ACC2001",
            new BigDecimal("15.00"), "INR");
        processing.markProcessing();
        when(state.createProcessing("demo-customer", request)).thenReturn(processing);
        when(gateway.transfer(transferId, "ACC1001", "ACC2001", new BigDecimal("15.00"), "INR"))
            .thenThrow(new RuntimeException("timeout"));

        var unknown = new Transfer(transferId, "idem-3", "demo-customer", "ACC1001", "ACC2001",
            new BigDecimal("15.00"), "INR");
        unknown.markProcessing();
        unknown.markReconciliationRequired("DOWNSTREAM_OUTCOME_UNKNOWN");
        when(state.markReconciliationRequired(transferId, "DOWNSTREAM_OUTCOME_UNKNOWN")).thenReturn(unknown);

        var service = new TransferApplicationService(repo, customers, gateway, state, risk);
        var result = service.create("demo-customer", request);

        assertEquals("RECONCILIATION_REQUIRED", result.status());
        verify(state).markReconciliationRequired(transferId, "DOWNSTREAM_OUTCOME_UNKNOWN");
    }
}
