package com.srinu.otelpoc.gateway.filter;

import com.srinu.otelpoc.cloud.CloudRunIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/** Keep customer Authorization intact; replace untrusted caller-supplied workload headers. */
@Component
public class CloudRunIdentityFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(CloudRunIdentityFilter.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectProvider<CloudRunIdentity> identity;
    public CloudRunIdentityFilter(ObjectProvider<CloudRunIdentity> identity) { this.identity = identity; }
    @Override public int getOrder() { return 10001; } // After RouteToRequestUrlFilter, before Netty routing.
    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        CloudRunIdentity credentials = identity.getIfAvailable();
        var clean = exchange.mutate().request(request -> request.headers(headers -> headers.remove(CloudRunIdentity.HEADER))).build();
        if (credentials == null) return chain.filter(clean);
        URI destination = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        String correlation = clean.getRequest().getHeaders().getFirst("X-Correlation-Id");
        if (correlation == null || correlation.isBlank()) correlation = UUID.randomUUID().toString();
        String correlationId = correlation;
        String audience = destination == null ? "unresolved" : destination.getScheme() + "://" + destination.getHost();
        return Mono.fromCallable(() -> credentials.bearerFor(destination)).subscribeOn(Schedulers.boundedElastic())
            // Handle only token acquisition here; downstream failures must not be relabeled as IAM failures.
            .map(token -> java.util.Optional.of(token))
            .onErrorResume(error -> {
                Throwable cause = error.getCause();
                log.warn("event=WORKLOAD_TOKEN_FAILED backend={} audience={} correlationId={} traceId={} errorType={} errorMessage={} causeType={} causeMessage={}",
                    destination == null ? "unresolved" : destination.getHost(), audience, correlationId, traceId(clean), error.getClass().getName(), safeMessage(error),
                    cause == null ? "none" : cause.getClass().getName(), safeMessage(cause));
                return tokenFailure(clean, correlationId).thenReturn(java.util.Optional.<String>empty());
            })
            .flatMap(token -> {
                if (token.isEmpty()) return Mono.empty();
                log.info("event=WORKLOAD_TOKEN_READY backend={} audience={} correlationId={} traceId={}",
                    destination.getHost(), audience, correlationId, traceId(clean));
                boolean login = java.util.Set.of("/api/v1/auth/login", "/api/v1/auth/register").contains(clean.getRequest().getPath().value());
                var outbound = clean.mutate().request(request -> request.headers(headers -> {
                    headers.set("X-Correlation-Id", correlationId);
                    headers.set(CloudRunIdentity.HEADER, token.get());
                    if (login) headers.set(HttpHeaders.AUTHORIZATION, token.get());
                    // Business requests retain the validated customer JWT for backend validation.
                })).build();
                return chain.filter(outbound).doOnSuccess(ignored -> log.info(
                    "event=PRIVATE_BACKEND_COMPLETED backend={} status={} correlationId={} traceId={}",
                    destination.getHost(), outbound.getResponse().getStatusCode(), correlationId, traceId(clean)));
            });
    }
    static String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null) return "unavailable";
        // Only our fixed diagnostic messages are safe; library errors can contain token response bodies.
        String message = error.getMessage();
        if (java.util.Set.of("Refusing workload credentials for an unconfigured service origin",
                "Interrupted while acquiring workload identity", "Metadata server returned an invalid ID token",
                "Metadata token audience or lifetime is invalid", "Metadata server returned malformed token claims",
                "Metadata identity response exceeded size limit").contains(message)
                || message.matches("Metadata identity request failed with HTTP [0-9]{3}")) return message;
        return "[redacted external exception message]";
    }
    private String traceId(ServerWebExchange exchange) {
        String active = MDC.get("trace_id");
        if (active != null) return active;
        String parent = exchange.getRequest().getHeaders().getFirst("traceparent");
        if (parent != null && parent.matches("[a-f0-9]{2}-[a-f0-9]{32}-[a-f0-9]{16}-[a-f0-9]{2}")) return parent.substring(3,35);
        return "unavailable";
    }
    private Mono<Void> tokenFailure(ServerWebExchange exchange, String correlationId) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("X-Correlation-Id", correlationId);
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of("code", "BACKEND_AUTHENTICATION_UNAVAILABLE",
                "message", "Unable to authenticate the gateway to the requested service", "correlationId", correlationId));
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (java.io.IOException error) { return exchange.getResponse().setComplete(); }
    }
}
