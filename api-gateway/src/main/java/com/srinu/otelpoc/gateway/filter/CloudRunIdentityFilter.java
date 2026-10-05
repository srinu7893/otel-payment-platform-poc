package com.srinu.otelpoc.gateway.filter;

import com.srinu.otelpoc.cloud.CloudRunIdentity;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.net.URI;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/** Keep customer Authorization intact; replace untrusted caller-supplied workload headers. */
@Component
public class CloudRunIdentityFilter implements GlobalFilter, Ordered {
    private final ObjectProvider<CloudRunIdentity> identity;
    public CloudRunIdentityFilter(ObjectProvider<CloudRunIdentity> identity) { this.identity = identity; }
    @Override public int getOrder() { return 10001; } // After RouteToRequestUrlFilter, before Netty routing.
    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        CloudRunIdentity credentials = identity.getIfAvailable();
        var clean = exchange.mutate().request(request -> request.headers(headers -> headers.remove(CloudRunIdentity.HEADER))).build();
        if (credentials == null) return chain.filter(clean);
        URI destination = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        return Mono.fromCallable(() -> credentials.bearerFor(destination)).subscribeOn(Schedulers.boundedElastic())
            .flatMap(token -> chain.filter(clean.mutate().request(request -> request.headers(headers ->
                headers.set(CloudRunIdentity.HEADER, token))).build()));
    }
}
