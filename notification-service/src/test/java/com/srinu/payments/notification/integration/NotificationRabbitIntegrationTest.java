package com.srinu.payments.notification.integration;

import com.srinu.payments.notification.config.RabbitConfig;
import com.srinu.payments.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class NotificationRabbitIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationRepository repository;

    @Test
    void completedPaymentEventIsDeliveredOnceEvenWhenRabbitRedeliversSameEvent() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventType\":\"PAYMENT_COMPLETED\",\"paymentId\":\"" + paymentId +
            "\",\"customerId\":\"demo-customer\",\"amount\":25.00}";

        publishPaymentEvent(payload, eventId, "rabbit-it-001");
        waitForSent(paymentId);

        publishPaymentEvent(payload, eventId, "rabbit-it-duplicate-001");
        Thread.sleep(750);

        var page = repository.findAllByPaymentId(paymentId, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        var record = page.getContent().getFirst();
        assertThat(record.getSourceEventId()).isEqualTo(eventId);
        assertThat(record.getCustomerId()).isEqualTo("demo-customer");
        assertThat(record.getChannel()).isEqualTo("EMAIL_SIMULATED");
        assertThat(record.getStatus()).isEqualTo("SENT");
        assertThat(record.getAttempts()).isEqualTo(1);
    }

    @Test
    void poisonEventIsRetriedAndDeadLetteredInsteadOfLoopingForever() {
        String poison = "{not-valid-json";
        UUID eventId = UUID.randomUUID();
        publishPaymentEvent(poison, eventId, "rabbit-it-poison-001");

        var deadLetter = rabbitTemplate.receive(RabbitConfig.DLQ, 12_000);

        assertThat(deadLetter).as("poison event should be routed to the notification DLQ").isNotNull();
        assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).isEqualTo(poison);
        assertThat(deadLetter.getMessageProperties().getHeaders()).containsKey("x-death");
        assertThat(deadLetter.getMessageProperties().getHeader("eventId")).isEqualTo(eventId.toString());
    }

    private void publishPaymentEvent(String payload, UUID eventId, String correlationId) {
        rabbitTemplate.convertAndSend(RabbitConfig.EVENT_EXCHANGE, "payment.completed", payload, message -> {
            message.getMessageProperties().setHeader("X-Correlation-Id", correlationId);
            message.getMessageProperties().setHeader("eventId", eventId.toString());
            message.getMessageProperties().setHeader("eventType", "PAYMENT_COMPLETED");
            return message;
        });
    }

    private void waitForSent(UUID paymentId) throws Exception {
        var deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            var page = repository.findAllByPaymentId(paymentId, PageRequest.of(0, 10));
            if (!page.isEmpty() && "SENT".equals(page.getContent().getFirst().getStatus())) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Notification was not consumed and persisted as SENT within 10 seconds");
    }
}
