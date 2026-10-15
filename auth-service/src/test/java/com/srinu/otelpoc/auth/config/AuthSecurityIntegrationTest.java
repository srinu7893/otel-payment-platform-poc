package com.srinu.otelpoc.auth.config;

import com.srinu.otelpoc.auth.api.AuthController;
import com.srinu.otelpoc.auth.service.AuthenticationService;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(classes=AuthSecurityIntegrationTest.TestApplication.class,
    webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"security.jwt.secret=test-auth-signing-key-at-least-thirty-two-bytes", "management.endpoint.health.probes.enabled=true",
        "management.endpoint.health.group.liveness.include=livenessState", "management.endpoint.health.group.readiness.include=readinessState,db",
        "management.endpoints.web.exposure.include=health,metrics"})
class AuthSecurityIntegrationTest {
    @Configuration
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class,SecurityErrorHandler.class,AuthController.class,com.srinu.otelpoc.auth.api.RegistrationController.class,HealthDiagnostics.class,CorrelationIdFilter.class,ProtectedController.class})
    static class TestApplication {
        @Bean AtomicBoolean databaseUp() { return new AtomicBoolean(true); }
        @Bean HealthIndicator dbHealthIndicator(AtomicBoolean databaseUp) {
            return () -> databaseUp.get() ? Health.up().build() : Health.down().build();
        }
    }
    @RestController static class ProtectedController {
        @GetMapping("/api/v1/auth/protected-test") String protectedRoute() { return "authenticated"; }
    }
    @Autowired TestRestTemplate http;
    @Autowired JwtEncoder encoder;
    @Autowired AtomicBoolean databaseUp;
    @Autowired org.springframework.context.ApplicationContext context;
    @MockitoBean AuthenticationService authenticationService;
    @MockitoBean com.srinu.otelpoc.auth.service.RegistrationService registration;
    @AfterEach void reset() {
        databaseUp.set(true);
        org.springframework.boot.availability.AvailabilityChangeEvent.publish(context, org.springframework.boot.availability.ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Test void healthAndBothProbesIgnoreAbsentOrMalformedApplicationBearer() {
        for (String path: new String[]{"/actuator/health","/actuator/health/liveness","/actuator/health/readiness"}) {
            for (String authorization: new String[]{"", "Bearer malformed", "Bearer invalid token"}) {
                var headers=new HttpHeaders();if (!authorization.isEmpty()) headers.set("Authorization",authorization);
                var response=http.exchange(path,HttpMethod.GET,new HttpEntity<>(headers),String.class);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).contains("UP");
            }
        }
    }
    @Test void loginIsPublicEvenWhenCloudRunTokenIsInAuthorization() {
        when(authenticationService.login("demo","demo123")).thenReturn(new AuthenticationService.LoginResult("customer-token","Bearer",1800,"demo-customer",Set.of("CUSTOMER")));
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.setBearerAuth("google-id-token");
        var response=http.exchange("/api/v1/auth/login",HttpMethod.POST,new HttpEntity<>("{\"username\":\"demo\",\"password\":\"demo123\"}",headers),String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("customer-token");
        headers.remove("Authorization");
        assertThat(http.exchange("/api/v1/auth/login",HttpMethod.POST,new HttpEntity<>("{\"username\":\"demo\",\"password\":\"demo123\"}",headers),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void protectedRoutesAndMetricsStillRequireApplicationJwt() {
        for (String path: new String[]{"/api/v1/auth/protected-test","/actuator/info","/actuator/metrics","/actuator/env","/actuator/configprops","/actuator/beans"})
            assertThat(http.getForEntity(path,String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Instant now=Instant.now();String jwt=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().subject("demo").issuedAt(now).expiresAt(now.plusSeconds(60)).build())).getTokenValue();
        var headers=new HttpHeaders();headers.setBearerAuth(jwt);
        assertThat(http.exchange("/api/v1/auth/protected-test",HttpMethod.GET,new HttpEntity<>(headers),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void registrationIsPublicAndIgnoresGoogleWorkloadBearer() {
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.setBearerAuth("google-id-token");
        String body="{\"username\":\"newuser\",\"password\":\"test-password-123\",\"name\":\"New User\",\"email\":\"new@example.com\"}";
        assertThat(http.exchange("/api/v1/auth/register",HttpMethod.POST,new HttpEntity<>(body,headers),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void databaseFailureAffectsReadinessButNotLiveness() {
        databaseUp.set(false);
        assertThat(http.getForEntity("/actuator/health/liveness",String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/actuator/health/readiness",String.class).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(http.getForEntity("/actuator/health",String.class).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
    @Test void refusingTrafficProducesOutOfServiceWithoutBreakingLiveness() {
        org.springframework.boot.availability.AvailabilityChangeEvent.publish(context, org.springframework.boot.availability.ReadinessState.REFUSING_TRAFFIC);
        var root=http.getForEntity("/actuator/health",String.class);
        assertThat(root.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(root.getBody()).contains("OUT_OF_SERVICE");
        assertThat(http.getForEntity("/actuator/health/liveness",String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
