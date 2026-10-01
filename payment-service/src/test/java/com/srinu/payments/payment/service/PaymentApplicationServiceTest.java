package com.srinu.payments.payment.service;

import com.srinu.payments.payment.api.PaymentRequest;
import com.srinu.payments.payment.client.GatewayClient;
import com.srinu.payments.payment.messaging.PaymentEventPublisher;
import com.srinu.payments.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentApplicationServiceTest {
    @Test
    void completesApprovedPayment() {
        var repo = mock(PaymentRepository.class);
        var gateway = mock(GatewayClient.class);
        var events = mock(PaymentEventPublisher.class);
        when(repo.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(gateway.authorize(any(), eq("ACC1001"), eq(new BigDecimal("50.00"))))
                .thenReturn(new GatewayClient.GatewayResult("COMPLETED", "Approved"));

        var service = new PaymentApplicationService(repo, gateway, events);
        var response = service.create(new PaymentRequest("k1", "ACC1001", "Demo Store", new BigDecimal("50.00")));

        assertEquals("COMPLETED", response.status());
        verify(events).completed(any(), eq(new BigDecimal("50.00")));
    }
}
