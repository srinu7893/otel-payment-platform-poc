package com.srinu.otelpoc.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
public class RequestContextFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(RequestContextFilter.class);
    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String USER_HEADER = "X-Authenticated-User";
    public static final String CUSTOMER_HEADER = "X-Customer-Id";
    public static final String ROLES_HEADER = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String finalCorrelationId = correlationId;
        return exchange.getPrincipal()
            .cast(Authentication.class)
            .defaultIfEmpty(new AnonymousAuthentication())
            .flatMap(authentication -> {
                var builder = exchange.getRequest().mutate();
                builder.headers(headers -> {
                    headers.remove(USER_HEADER);
                    headers.remove(CUSTOMER_HEADER);
                    headers.remove(ROLES_HEADER);
                    headers.set(CORRELATION_HEADER, finalCorrelationId);
                    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                        headers.set(USER_HEADER, jwtAuth.getName());
                        String customerId = jwtAuth.getToken().getClaimAsString("customer_id");
                        if (customerId != null) headers.set(CUSTOMER_HEADER, customerId);
                        List<String> roles = jwtAuth.getToken().getClaimAsStringList("roles");
                        if (roles != null) headers.set(ROLES_HEADER, String.join(",", roles));
                    }
                });

                ServerWebExchange mutated = exchange.mutate()
                    .request(builder.build())
                    .response(exchange.getResponse())
                    .build();
                mutated.getResponse().getHeaders().set(CORRELATION_HEADER, finalCorrelationId);

                long started = System.currentTimeMillis();
                log.info("event=EDGE_REQUEST_STARTED method={} path={} correlationId={} user={}",
                    exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(), finalCorrelationId,
                    authentication.getName());

                return chain.filter(mutated)
                    .doOnSuccess(ignored -> log.info(
                        "event=EDGE_REQUEST_COMPLETED method={} path={} status={} correlationId={} durationMs={}",
                        exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(),
                        exchange.getResponse().getStatusCode(), finalCorrelationId,
                        System.currentTimeMillis() - started));
            });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    private static final class AnonymousAuthentication implements Authentication {
        @Override public java.util.Collection<org.springframework.security.core.GrantedAuthority> getAuthorities() { return List.of(); }
        @Override public Object getCredentials() { return ""; }
        @Override public Object getDetails() { return null; }
        @Override public Object getPrincipal() { return "anonymous"; }
        @Override public boolean isAuthenticated() { return false; }
        @Override public void setAuthenticated(boolean isAuthenticated) { }
        @Override public String getName() { return "anonymous"; }
    }
}
