package com.srinu.payments.payment.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CustomerClient {
    private final RestClient client;

    public CustomerClient(RestClient.Builder builder, @Value("${clients.customer.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public CustomerView get(String customerId) {
        return client.get()
            .uri("/api/v1/customers/{id}", customerId)
            .retrieve()
            .body(CustomerView.class);
    }

    public record CustomerView(String id, String name, String email, String accountNumber, boolean active) {}
}
