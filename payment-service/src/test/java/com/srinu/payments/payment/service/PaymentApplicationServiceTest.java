package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.PaymentRequest;
import com.srinu.payments.payment.client.CustomerClient;
import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.messaging.PaymentEventPublisher;
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
        var events = mock(PaymentEventPublisher.class);
        when(repo.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(customers.get("demo-customer"))
            .thenReturn(new CustomerClient.CustomerView("demo-customer", "Demo", "demo@example.com", "ACC1001", true));
        when(gateway.authorize(any(), eq("ACC1001"), eq(new BigDecimal("50.00"))))
            .thenReturn(new GatewayClient.GatewayResult(UUID.randomUUID(), "COMPLETED", "Approved"));

        var service = new PaymentApplicationService(repo, customers, gateway, events);
        var response = service.create("demo-customer",
            new PaymentRequest("k1", "ACC1001", "Demo Store", new BigDecimal("50.00")));

        assertEquals("COMPLETED", response.status());
        assertEquals("Demo Store", response.merchant());
        verify(events).completed(any(), eq("demo-customer"), eq(new BigDecimal("50.00")));
    }

    @Test
    void rejectsPaymentFromAccountNotOwnedByCustomer() {
        var repo = mock(PaymentRepository.class);
        var customers = mock(CustomerClient.class);
        var gateway = mock(GatewayClient.class);
        var events = mock(PaymentEventPublisher.class);
        when(repo.findByIdempotencyKey("k2")).thenReturn(Optional.empty());
        when(customers.get("demo-customer"))
            .thenReturn(new CustomerClient.CustomerView("demo-customer", "Demo", "demo@example.com", "ACC1001", true));

        var service = new PaymentApplicationService(repo, customers, gateway, events);

        assertThrows(PaymentApplicationService.PaymentAuthorizationException.class,
            () -> service.create("demo-customer",
                new PaymentRequest("k2", "ACC2001", "Demo Store", new BigDecimal("25.00"))));
        verifyNoInteractions(gateway, events);
    }
}
