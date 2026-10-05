package com.srinu.otelpoc.gateway.filter;

import com.srinu.otelpoc.cloud.CloudRunIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

class CloudRunIdentityFilterTest {
    @Test void replacesWorkloadHeaderAndPreservesCustomerJwt() {
        String audience = "https://payment.example.run.app";
        String token = "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(
            ("{\"aud\":\"" + audience + "\",\"exp\":5000}").getBytes(StandardCharsets.UTF_8)) + ".sig";
        var credentials = new CloudRunIdentity(Set.of(audience), value -> token,
            Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC));
        ObjectProvider<CloudRunIdentity> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(credentials);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/payments")
            .header("Authorization", "Bearer customer-jwt").header(CloudRunIdentity.HEADER, "Bearer attacker"));
        exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create(audience + "/api/v1/payments"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        new CloudRunIdentityFilter(provider).filter(exchange, next -> { forwarded.set(next); return Mono.empty(); }).block();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer customer-jwt");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(CloudRunIdentity.HEADER)).isEqualTo("Bearer " + token);
    }
    @Test void localModeRemovesWorkloadHeaderWithoutFetchingMetadata() {
        ObjectProvider<CloudRunIdentity> provider = mock(ObjectProvider.class);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/payments")
            .header(CloudRunIdentity.HEADER, "Bearer attacker"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        new CloudRunIdentityFilter(provider).filter(exchange, next -> { forwarded.set(next); return Mono.empty(); }).block();
        assertThat(forwarded.get().getRequest().getHeaders()).doesNotContainKey(CloudRunIdentity.HEADER);
    }
}
