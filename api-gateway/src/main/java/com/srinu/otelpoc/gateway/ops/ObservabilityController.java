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
import java.util.List;

@RestController
@RequestMapping("/api/v1/ops/observability")
public class ObservabilityController {
    private final List<ObservabilityTarget> targets;

    public ObservabilityController(
        @Value("${observability.collector-url:http://localhost:13133}") String collector,
        @Value("${observability.jaeger-url:http://localhost:16686}") String jaeger,
        @Value("${observability.prometheus-url:http://localhost:9090}") String prometheus,
        @Value("${observability.grafana-url:http://localhost:3001}") String grafana,
        @Value("${observability.jaeger-ui-url:http://localhost:16686}") String jaegerUi,
        @Value("${observability.prometheus-ui-url:http://localhost:9090}") String prometheusUi,
        @Value("${observability.grafana-ui-url:http://localhost:3001}") String grafanaUi) {
        this.targets = List.of(
            new ObservabilityTarget("otel-collector", collector, "/", null),
            new ObservabilityTarget("jaeger", jaeger, "/api/services", jaegerUi),
            new ObservabilityTarget("prometheus", prometheus, "/-/ready", prometheusUi),
            new ObservabilityTarget("grafana", grafana, "/api/health", grafanaUi)
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    public Mono<ObservabilityResponse> status() {
        return Flux.fromIterable(targets)
            .flatMap(this::probe)
            .collectList()
            .map(items -> new ObservabilityResponse(
                items.stream().allMatch(ObservabilityComponent::healthy),
                Instant.now(),
                items));
    }

    private Mono<ObservabilityComponent> probe(ObservabilityTarget target) {
        Instant started = Instant.now();
        return WebClient.create(target.baseUrl())
            .get()
            .uri(target.path())
            .retrieve()
            .toBodilessEntity()
            .timeout(Duration.ofSeconds(3))
            .map(response -> new ObservabilityComponent(
                target.name(),
                response.getStatusCode().is2xxSuccessful(),
                response.getStatusCode().value(),
                Duration.between(started, Instant.now()).toMillis(),
                target.uiUrl(),
                null))
            .onErrorResume(ex -> Mono.just(new ObservabilityComponent(
                target.name(),
                false,
                null,
                Duration.between(started, Instant.now()).toMillis(),
                target.uiUrl(),
                ex.getClass().getSimpleName())));
    }

    private record ObservabilityTarget(String name, String baseUrl, String path, String uiUrl) {}

    public record ObservabilityComponent(
        String component,
        boolean healthy,
        Integer httpStatus,
        long latencyMs,
        String uiUrl,
        String error) {}

    public record ObservabilityResponse(
        boolean allHealthy,
        Instant checkedAt,
        List<ObservabilityComponent> components) {}
}
