package com.srinu.otelpoc.gateway.filter;
import com.srinu.otelpoc.gateway.ApiGatewayApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.cloud.gateway.filter.*;
import java.util.List;

@SpringBootTest(classes={ApiGatewayApplication.class,CustomerRouteSecurityTest.Backend.class},webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"security.jwt.secret=customer-route-security-test-key-at-least-thirty-two-bytes","cloud.run.iam.enabled=false"})
class CustomerRouteSecurityTest {
    static final String SECRET="customer-route-security-test-key-at-least-thirty-two-bytes";
    @LocalServerPort int port;
    @TestConfiguration static class Backend {
        @Bean GlobalFilter testBackend(){
            class Terminal implements GlobalFilter,org.springframework.core.Ordered {
                public int getOrder(){return -100;}
                public reactor.core.publisher.Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange,GatewayFilterChain chain){exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.OK);return exchange.getResponse().setComplete();}
            }
            return new Terminal();
        }
    }
    String jwt(String role)throws Exception{
        var jwt=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),new com.nimbusds.jwt.JWTClaimsSet.Builder().subject("owner").claim("customer_id","owner").claim("roles",List.of(role)).expirationTime(new java.util.Date(System.currentTimeMillis()+60000)).build());
        jwt.sign(new com.nimbusds.jose.crypto.MACSigner(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)));return jwt.serialize();
    }
    WebTestClient client(){return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:"+port).build();}
    @Test void customersCanReadOnlyTheirOwnProfile()throws Exception{
        client().get().uri("/api/v1/customers/owner").headers(h->h.setBearerAuth(jwtUnchecked("CUSTOMER"))).exchange().expectStatus().isOk();
        for(String path:List.of("/api/v1/customers/other","/api/v1/customers"))client().get().uri(path).headers(h->h.setBearerAuth(jwtUnchecked("CUSTOMER"))).exchange().expectStatus().isForbidden();
    }
    @Test void customersCannotRelinkOrCreateBankProfiles()throws Exception{
        for(var method:List.of(org.springframework.http.HttpMethod.POST,org.springframework.http.HttpMethod.PUT,org.springframework.http.HttpMethod.PATCH,org.springframework.http.HttpMethod.DELETE))
            client().method(method).uri("/api/v1/customers/owner").headers(h->h.setBearerAuth(jwtUnchecked("CUSTOMER"))).exchange().expectStatus().isForbidden();
    }
    @Test void supportCanReadAndAdminCanManageProfiles()throws Exception{
        client().get().uri("/api/v1/customers/other").headers(h->h.setBearerAuth(jwtUnchecked("SUPPORT"))).exchange().expectStatus().isOk();
        client().put().uri("/api/v1/customers/owner").headers(h->h.setBearerAuth(jwtUnchecked("ADMIN"))).exchange().expectStatus().isOk();
    }
    String jwtUnchecked(String role){try{return jwt(role);}catch(Exception e){throw new IllegalStateException(e);}}
}
