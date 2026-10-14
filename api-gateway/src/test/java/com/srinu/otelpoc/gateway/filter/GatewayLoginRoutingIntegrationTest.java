package com.srinu.otelpoc.gateway.filter;
import com.srinu.otelpoc.cloud.CloudRunIdentity;
import com.srinu.otelpoc.gateway.ApiGatewayApplication;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes={ApiGatewayApplication.class,GatewayLoginRoutingIntegrationTest.BackendConfig.class},webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"cloud.run.iam.enabled=true","cloud.run.audiences=https://auth-service-fddyka6g2a-uc.a.run.app",
        "SERVICES_AUTH_URL=https://auth-service-fddyka6g2a-uc.a.run.app", "security.jwt.secret=local-test-signing-key-at-least-thirty-two-bytes"})
class GatewayLoginRoutingIntegrationTest {
    static volatile int backendPort;
    @org.springframework.boot.test.context.TestConfiguration
    static class BackendConfig {
        @org.springframework.context.annotation.Bean org.springframework.cloud.gateway.filter.GlobalFilter testBackendRoute() {
            class Route implements org.springframework.cloud.gateway.filter.GlobalFilter, org.springframework.core.Ordered {
                public int getOrder(){return 10002;}
                public reactor.core.publisher.Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange,org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
                    if(backendPort!=0) exchange.getAttributes().put(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR,
                        URI.create("http://127.0.0.1:"+backendPort+exchange.getRequest().getPath().value()));
                    return chain.filter(exchange);
                }
            }
            return new Route();
        }
    }
    @LocalServerPort int port;
    @MockitoBean CloudRunIdentity identity;
    @Test void downstreamReceivesTokenBodyAndTraceHeaders() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> body=new AtomicReference<>();
        AtomicReference<com.sun.net.httpserver.Headers> headers=new AtomicReference<>();
        server.createContext("/api/v1/auth/login",request->{
            headers.set(request.getRequestHeaders());body.set(new String(request.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            byte[] response="{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            request.getResponseHeaders().set("Content-Type","application/json");request.sendResponseHeaders(200,response.length);
            request.getResponseBody().write(response);request.close();
        });
        server.start();backendPort=server.getAddress().getPort();
        String payload="{\"username\":\"demo\",\"password\":\"demo123\"}";
        String parent="00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";
        when(identity.bearerFor(any())).thenReturn("Bearer TEST_TOKEN");
        try {
            WebTestClient.bindToServer().baseUrl("http://127.0.0.1:"+port).build().post().uri("/api/v1/auth/login")
                .header("Content-Type","application/json").header("X-Correlation-Id","downstream-verification")
                .header("traceparent",parent).header("tracestate","test=value").header("baggage","test=value")
                .bodyValue(payload).exchange().expectStatus().isOk().expectBody().jsonPath("$.ok").isEqualTo(true);
            assertThat(body.get()).isEqualTo(payload);
            assertThat(headers.get().getFirst("Authorization")).isEqualTo("Bearer TEST_TOKEN");
            assertThat(headers.get().getFirst("X-Correlation-Id")).isEqualTo("downstream-verification");
            assertThat(headers.get().getFirst("traceparent")).isEqualTo(parent);
            assertThat(headers.get().getFirst("tracestate")).isEqualTo("test=value");
            assertThat(headers.get().getFirst("baggage")).isEqualTo("test=value");
            assertThat(headers.get().getFirst("Content-Type")).startsWith("application/json");
        } finally {backendPort=0;server.stop(0);}
    }
    @Test void diagnosticMessagesNeverExposeExternalCredentials() {
        assertThat(CloudRunIdentityFilter.safeMessage(new java.io.IOException("Bearer TEST_TOKEN password=secret"))).doesNotContain("TEST_TOKEN","secret");
        assertThat(CloudRunIdentityFilter.safeMessage(new java.io.IOException("Refusing workload credentials for an unconfigured service origin"))).contains("unconfigured service origin");
    }
    @Test void actualRouteResolvesCanonicalDestinationBeforeTokenAcquisition() throws Exception {
        AtomicReference<URI> destination=new AtomicReference<>();
        AtomicReference<String> audience=new AtomicReference<>();
        var real=new CloudRunIdentity(java.util.Set.of("https://auth-service-fddyka6g2a-uc.a.run.app"),value->{audience.set(value);throw new java.io.IOException("controlled token failure");},java.time.Clock.systemUTC());
        when(identity.bearerFor(any())).thenAnswer(call->{destination.set(call.getArgument(0));return real.bearerFor(call.getArgument(0));});
        WebTestClient.bindToServer().baseUrl("http://127.0.0.1:"+port).build().post().uri("/api/v1/auth/login")
            .header("Content-Type","application/json").header("X-Correlation-Id","route-verification")
            .bodyValue("{\"username\":\"demo\",\"password\":\"demo123\"}").exchange()
            .expectStatus().isEqualTo(503).expectBody().jsonPath("$.code").isEqualTo("BACKEND_AUTHENTICATION_UNAVAILABLE");
        assertThat(destination.get()).isEqualTo(URI.create("https://auth-service-fddyka6g2a-uc.a.run.app:443/api/v1/auth/login"));
        assertThat(audience.get()).isEqualTo("https://auth-service-fddyka6g2a-uc.a.run.app");
    }
}
