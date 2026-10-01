package com.srinu.otelpoc.gateway.ops;

import org.junit.jupiter.api.Test;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsControllerTest {

    @Test
    void aggregatesAllConfiguredServicesAsHealthy() {
        DisposableServer server = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .route(routes -> routes.get("/actuator/health",
                (request, response) -> response
                    .header("Content-Type", "application/json")
                    .sendString(reactor.core.publisher.Mono.just("{\"status\":\"UP\"}"))))
            .bindNow();

        try {
            String url = "http://127.0.0.1:" + server.port();
            var controller = new OperationsController(url, url, url, url, url, url);

            var result = controller.health().block();

            assertThat(result).isNotNull();
            assertThat(result.allHealthy()).isTrue();
            assertThat(result.services()).hasSize(6);
            assertThat(result.services()).allSatisfy(service -> {
                assertThat(service.status()).isEqualTo("UP");
                assertThat(service.error()).isNull();
                assertThat(service.latencyMs()).isGreaterThanOrEqualTo(0);
            });
        } finally {
            server.disposeNow();
        }
    }
}
