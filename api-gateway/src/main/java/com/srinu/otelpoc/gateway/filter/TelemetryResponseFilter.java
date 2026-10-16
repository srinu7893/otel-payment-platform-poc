package com.srinu.otelpoc.gateway.filter;

import io.opentelemetry.api.trace.Span;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import java.util.UUID;

/** Exposes the agent's actual request trace, including security rejections. No SDK is installed here. */
@Component
public class TelemetryResponseFilter implements WebFilter, Ordered {
    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            String supplied = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
            String correlation = supplied != null && supplied.matches("[A-Za-z0-9._-]{1,100}")
                ? supplied : UUID.randomUUID().toString();
            var context = Span.current().getSpanContext();
            var request = exchange.getRequest().mutate().headers(headers -> {
                headers.set("X-Correlation-Id", correlation);
                headers.remove("X-Trace-Id");
            }).build();
            exchange.getResponse().beforeCommit(() -> {
                var headers = exchange.getResponse().getHeaders();
                headers.set("X-Correlation-Id", correlation);
                headers.remove("X-Trace-Id");
                if (context.isValid()) headers.set("X-Trace-Id", context.getTraceId());
                return Mono.empty();
            });
            return chain.filter(exchange.mutate().request(request).build());
        });
    }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
