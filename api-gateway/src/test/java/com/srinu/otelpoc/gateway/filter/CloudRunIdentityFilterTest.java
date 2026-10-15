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
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"/api/v1/auth/login","/api/v1/auth/register"})
    void publicAuthUsesCanonicalAudienceAndGoogleAuthorizationWhilePreservingBodyAndTrace(String path) {
        String audience="https://auth-service-fddyka6g2a-uc.a.run.app";
        AtomicReference<String> requestedAudience=new AtomicReference<>();
        String token="header."+Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"aud\":\""+audience+"\",\"exp\":5000}").getBytes(StandardCharsets.UTF_8))+".sig";
        var identity=new CloudRunIdentity(Set.of(audience), value->{requestedAudience.set(value);return token;},Clock.fixed(Instant.ofEpochSecond(1000),ZoneOffset.UTC));
        ObjectProvider<CloudRunIdentity> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(identity);
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.post(path)
            .header("X-Correlation-Id","login-corr").header("traceparent","00-cdb7d9d2d2fd09f5471664c22133068d-0123456789abcdef-01")
            .header("tracestate","vendor=opaque").header("baggage","demo=value").body("{\"username\":\"demo\",\"password\":\"demo123\"}"));
        exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR,URI.create(audience+path));
        AtomicReference<ServerWebExchange> outbound=new AtomicReference<>();
        new CloudRunIdentityFilter(provider).filter(exchange,next->{outbound.set(next);return Mono.empty();}).block();
        assertThat(requestedAudience.get()).isEqualTo(audience);
        var headers=outbound.get().getRequest().getHeaders();
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer "+token);
        assertThat(headers.getFirst(CloudRunIdentity.HEADER)).isEqualTo("Bearer "+token);
        assertThat(headers.getFirst("X-Correlation-Id")).isEqualTo("login-corr");
        for (String header:new String[]{"traceparent","tracestate","baggage"})assertThat(headers.getFirst(header)).isEqualTo(exchange.getRequest().getHeaders().getFirst(header));
        assertThat(org.springframework.core.io.buffer.DataBufferUtils.join(outbound.get().getRequest().getBody())
            .map(buffer->{byte[] bytes=new byte[buffer.readableByteCount()];buffer.read(bytes);org.springframework.core.io.buffer.DataBufferUtils.release(buffer);return new String(bytes,StandardCharsets.UTF_8);}).block()).contains("demo123");
    }
    @Test void tokenFailureIsSanitizedStructured503AndDoesNotCallBackend() {
        String audience="https://auth.run.app";
        var credentials=new CloudRunIdentity(Set.of(audience), value->{throw new java.io.IOException("Bearer leaked-token at https://metadata.internal secret=private");},Clock.systemUTC());
        ObjectProvider<CloudRunIdentity> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(credentials);
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login").header("X-Correlation-Id","failure-corr"));
        exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR,URI.create(audience+"/api/v1/auth/login"));
        new CloudRunIdentityFilter(provider).filter(exchange,next->{throw new AssertionError("Backend must not be called");}).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("BACKEND_AUTHENTICATION_UNAVAILABLE","failure-corr")
            .doesNotContain("leaked-token","metadata.internal","private","IOException");
    }
    @Test void downstreamFailureIsNotRelabeledAsTokenFailure() {
        ObjectProvider<CloudRunIdentity> provider=mock(ObjectProvider.class);CloudRunIdentity identity=mock(CloudRunIdentity.class);
        when(provider.getIfAvailable()).thenReturn(identity);
        try {when(identity.bearerFor(any())).thenReturn("Bearer google");}catch(Exception e){throw new AssertionError(e);}
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/payments"));
        exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR,URI.create("https://payment.run.app/api/v1/payments"));
        assertThatThrownBy(()->new CloudRunIdentityFilter(provider).filter(exchange,next->Mono.error(new IllegalStateException("backend error"))).block()).isInstanceOf(IllegalStateException.class);
    }
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
