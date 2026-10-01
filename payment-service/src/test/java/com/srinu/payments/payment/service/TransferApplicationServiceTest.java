package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.TransferRequest;
import com.srinu.payments.payment.client.CustomerClient;
import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.domain.Transfer;
import com.srinu.payments.payment.messaging.PaymentEventPublisher;
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
        var events = mock(PaymentEventPublisher.class);

        when(repo.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(repo.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customers.get("demo-customer"))
            .thenReturn(new CustomerClient.CustomerView("demo-customer", "Demo", "demo@example.com", "ACC1001", true));
        var bankTx = UUID.randomUUID();
        when(gateway.transfer(any(), eq("ACC1001"), eq("ACC2001"), eq(new BigDecimal("125.00")), eq("INR")))
            .thenReturn(new GatewayClient.TransferGatewayResult(bankTx, "COMPLETED", "Transfer completed"));

        var service = new TransferApplicationService(repo, customers, gateway, events);
        var result = service.create("demo-customer",
            new TransferRequest("idem-1", "ACC1001", "ACC2001", new BigDecimal("125.00"), "INR"));

        assertEquals("COMPLETED", result.status());
        assertEquals(bankTx, result.bankTransactionId());
        assertEquals("****1001", result.senderAccount());
        assertEquals("****2001", result.receiverAccount());
        verify(events).transferCompleted(any(), eq(bankTx), eq("demo-customer"), eq("ACC1001"), eq("ACC2001"),
            eq(new BigDecimal("125.00")), eq("INR"));
    }

    @Test
    void rejectsSenderAccountThatDoesNotBelongToAuthenticatedCustomer() {
        var repo = mock(TransferRepository.class);
        var customers = mock(CustomerClient.class);
        var gateway = mock(GatewayClient.class);
        var events = mock(PaymentEventPublisher.class);

        when(repo.findByIdempotencyKey("idem-2")).thenReturn(Optional.empty());
        when(customers.get("demo-customer"))
            .thenReturn(new CustomerClient.CustomerView("demo-customer", "Demo", "demo@example.com", "ACC1001", true));

        var service = new TransferApplicationService(repo, customers, gateway, events);

        assertThrows(TransferApplicationService.TransferAuthorizationException.class,
            () -> service.create("demo-customer",
                new TransferRequest("idem-2", "ACC9999", "ACC2001", new BigDecimal("25.00"), "INR")));
        verifyNoInteractions(gateway, events);
    }

    @Test
    void returnsPriorTransferForIdempotentReplay() {
        var repo = mock(TransferRepository.class);
        var customers = mock(CustomerClient.class);
        var gateway = mock(GatewayClient.class);
        var events = mock(PaymentEventPublisher.class);

        var existing = new Transfer(UUID.randomUUID(), "same-key", "demo-customer", "ACC1001", "ACC2001",
            new BigDecimal("10.00"), "INR");
        when(repo.findByIdempotencyKey("same-key")).thenReturn(Optional.of(existing));

        var service = new TransferApplicationService(repo, customers, gateway, events);
        var result = service.create("demo-customer",
            new TransferRequest("same-key", "ACC1001", "ACC2001", new BigDecimal("10.00"), "INR"));

        assertEquals(existing.getId(), result.transferId());
        assertEquals("PENDING", result.status());
        verifyNoInteractions(customers, gateway, events);
    }
}
