package com.srinu.otelpoc.gateway.ops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ops")
public class OperationsController {
    private final List<ServiceTarget> targets;

    public OperationsController(
        @Value("${services.auth-url:http://localhost:8079}") String auth,
        @Value("${services.customer-url:http://localhost:8084}") String customer,
        @Value("${services.payment-url:http://localhost:8080}") String payment,
        @Value("${services.notification-url:http://localhost:8083}") String notification,
        @Value("${services.gateway-url:http://localhost:8081}") String gateway,
        @Value("${services.bank-url:http://localhost:8082}") String bank) {
        this.targets = List.of(
            new ServiceTarget("auth-service", auth),
            new ServiceTarget("customer-service", customer),
            new ServiceTarget("payment-service", payment),
            new ServiceTarget("gateway-service", gateway),
            new ServiceTarget("mock-bank-service", bank),
            new ServiceTarget("notification-service", notification)
        );
    }

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    public Mono<OperationsHealthResponse> health() {
        return Flux.fromIterable(targets)
            .flatMap(this::probe)
            .collectList()
            .map(items -> new OperationsHealthResponse(
                items.stream().allMatch(i -> "UP".equals(i.status())),
                Instant.now(),
                items));
    }

    private Mono<ServiceHealth> probe(ServiceTarget target) {
        Instant started = Instant.now();
        return WebClient.create(target.baseUrl())
            .get()
            .uri("/actuator/health")
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofSeconds(3))
            .map(body -> {
                Object raw = body.get("status");
                String status = raw == null ? "UNKNOWN" : raw.toString();
                return new ServiceHealth(target.name(), status,
                    Duration.between(started, Instant.now()).toMillis(), null);
            })
            .onErrorResume(ex -> Mono.just(new ServiceHealth(
                target.name(), "DOWN", Duration.between(started, Instant.now()).toMillis(),
                ex.getClass().getSimpleName())));
    }

    private record ServiceTarget(String name, String baseUrl) {}
    public record ServiceHealth(String service, String status, long latencyMs, String error) {}
    public record OperationsHealthResponse(boolean allHealthy, Instant checkedAt, List<ServiceHealth> services) {}
}
