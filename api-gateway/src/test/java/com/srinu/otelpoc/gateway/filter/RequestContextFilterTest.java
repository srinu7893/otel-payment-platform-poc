package com.srinu.otelpoc.gateway.filter;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.assertThat;
class RequestContextFilterTest {
    @Test void removesBrowserIdentityHeadersForAnonymousLogin() {
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login")
            .header("X-Authenticated-User","attacker").header("X-Customer-Id","victim").header("X-User-Roles","ADMIN"));
        AtomicReference<ServerWebExchange> outbound=new AtomicReference<>();
        new RequestContextFilter().filter(exchange,next->{outbound.set(next);return Mono.empty();}).block();
        assertThat(outbound.get().getRequest().getHeaders()).doesNotContainKeys("X-Authenticated-User","X-Customer-Id","X-User-Roles");
    }
    @Test void onlyAuthenticatedJwtClaimsReplaceUntrustedHeaders() {
        Jwt jwt=Jwt.withTokenValue("validated-token").header("alg","HS256").subject("demo").issuedAt(Instant.now())
            .claim("customer_id","demo-customer").claim("roles",List.of("CUSTOMER")).build();
        var principal=new JwtAuthenticationToken(jwt,List.of(),"demo");
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/payments").header("X-Customer-Id","victim").header("Authorization","Bearer validated-token"));
        AtomicReference<ServerWebExchange> outbound=new AtomicReference<>();
        new RequestContextFilter().filter(exchange.mutate().principal(Mono.just(principal)).build(),next->{outbound.set(next);return Mono.empty();}).block();
        assertThat(outbound.get().getRequest().getHeaders().getFirst("X-Customer-Id")).isEqualTo("demo-customer");
        assertThat(outbound.get().getRequest().getHeaders().getFirst("X-User-Roles")).isEqualTo("CUSTOMER");
        assertThat(outbound.get().getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer validated-token");
    }
}
