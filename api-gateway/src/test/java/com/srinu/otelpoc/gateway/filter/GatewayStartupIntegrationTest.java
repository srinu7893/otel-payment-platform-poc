package com.srinu.otelpoc.gateway.filter;

import com.srinu.otelpoc.gateway.ApiGatewayApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(classes=ApiGatewayApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"cloud.run.iam.enabled=true", "cloud.run.audiences=https://auth-service-fddyka6g2a-uc.a.run.app",
        "SERVICES_AUTH_URL=https://auth-service-fddyka6g2a-uc.a.run.app", "security.jwt.secret=local-test-signing-key-at-least-thirty-two-bytes",
        "management.endpoint.health.probes.enabled=true", "management.endpoints.web.exposure.include=health,info"})
class GatewayStartupIntegrationTest {
    @Autowired org.springframework.context.ApplicationContext context;
    @LocalServerPort int port;
    @Test void cloudIamEnabledGatewayStartsAndProbesArePublicAtApplicationLayer() {
        org.assertj.core.api.Assertions.assertThat(context.containsBean("jsonToGRPCFilterFactory")).isFalse();
        org.assertj.core.api.Assertions.assertThat(context.getBean(com.srinu.otelpoc.cloud.CloudRunIdentity.class)).isNotNull();
        var client=WebTestClient.bindToServer().baseUrl("http://127.0.0.1:"+port).build();
        for (String path:new String[]{"/actuator/health/liveness","/actuator/health/readiness"})
            client.get().uri(path).exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
