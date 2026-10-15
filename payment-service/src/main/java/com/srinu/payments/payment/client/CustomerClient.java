package com.srinu.payments.payment.client;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class CustomerClient {
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private final RestClient client;

    public CustomerClient(RestClient.Builder builder, @Value("${clients.customer.base-url}") String baseUrl,
                          @Value("${clients.customer.read-timeout:25s}") java.time.Duration readTimeout) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(3));
        factory.setReadTimeout(readTimeout);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public CustomerView get(String customerId) {
        return client.get()
            .uri("/api/v1/customers/{id}", customerId)
            .header(CORRELATION_HEADER, correlationId())
            .retrieve()
            .body(CustomerView.class);
    }

    private String correlationId() {
        String value = MDC.get("correlationId");
        return value == null ? UUID.randomUUID().toString() : value;
    }

    public record CustomerView(String id, String name, String email, String accountNumber, boolean active) {}
}
