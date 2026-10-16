package com.srinu.otelpoc.gateway.filter;

import io.opentelemetry.api.trace.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.assertThat;

class TelemetryResponseFilterTest {
    @Test void reportsActiveTraceOnRejectedRequestAndStripsSpoofedHeader() {
        String trace = "1234567890abcdef1234567890abcdef";
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/payments")
            .header("X-Trace-Id", "spoofed").header("X-Correlation-Id", "safe-correlation"));
        var span = Span.wrap(SpanContext.create(trace, "1234567890abcdef", TraceFlags.getSampled(), TraceState.getDefault()));
        try (var scope = span.makeCurrent()) {
            new TelemetryResponseFilter().filter(exchange, next -> {
                assertThat(next.getRequest().getHeaders().getFirst("X-Trace-Id")).isNull();
                next.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                next.getResponse().getHeaders().set("X-Trace-Id", "downstream-spoof");
                return next.getResponse().setComplete();
            }).block();
        }
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).isEqualTo(trace);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id")).isEqualTo("safe-correlation");
    }
    @Test void doesNotInventTraceWithoutAgentAndBoundsCorrelation() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
            .header("X-Correlation-Id", "unsafe value "+"x".repeat(150)));
        new TelemetryResponseFilter().filter(exchange, next -> next.getResponse().setComplete()).block();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).isNull();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id"))
            .matches("[0-9a-f-]{36}");
    }
}
