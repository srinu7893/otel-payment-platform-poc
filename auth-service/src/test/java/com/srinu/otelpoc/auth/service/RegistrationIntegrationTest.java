package com.srinu.otelpoc.auth.service;
import com.srinu.otelpoc.auth.repository.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"security.jwt.secret=registration-test-secret-at-least-thirty-two-bytes","cloud.run.iam.enabled=false","debug=false","logging.level.root=INFO"})
class RegistrationIntegrationTest {
    static final AtomicBoolean failBank=new AtomicBoolean(false);
    static final AtomicReference<String> customerBody=new AtomicReference<>();
    static final AtomicReference<String> bankBody=new AtomicReference<>();
    static HttpServer backend;
    static {
        try {
            backend=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            backend.createContext("/internal/onboarding/customers/",exchange->{customerBody.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));exchange.sendResponseHeaders(200,-1);exchange.close();});
            backend.createContext("/internal/onboarding/accounts/",exchange->{bankBody.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));exchange.sendResponseHeaders(failBank.get()?503:200,-1);exchange.close();});
            backend.start();
        }catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        if(Boolean.getBoolean("poc.native.integration")){
            p.add("spring.datasource.url",()->"jdbc:postgresql://127.0.0.1:55432/poc_integration");p.add("spring.datasource.username",()->"payments");p.add("spring.datasource.password",()->"payments");
        }else{
            var pg=new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16-alpine");pg.start();p.add("spring.datasource.url",pg::getJdbcUrl);p.add("spring.datasource.username",pg::getUsername);p.add("spring.datasource.password",pg::getPassword);
        }
        p.add("clients.customer.base-url",()->"http://127.0.0.1:"+backend.getAddress().getPort());p.add("clients.bank.base-url",()->"http://127.0.0.1:"+backend.getAddress().getPort());
    }
    @Autowired TestRestTemplate http;
    @Autowired AppUserRepository users;
    @Autowired RegistrationRepository registrations;
    Map<String,String> request(){var suffix=UUID.randomUUID().toString().replace("-","").substring(0,12);return Map.of("username","test_"+suffix,"password","Registration-test-123","name","New Customer","email",suffix+"@example.com");}
    @Test void registrationCreatesHashedCustomerCredentialsAndReplaysWithoutAnotherAccount(){
        var request=request();var response=http.postForEntity("/api/v1/auth/register",request,Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String account=(String)response.getBody().get("accountNumber");assertThat(account).matches("ACC[A-F0-9]{24}");
        var user=users.findById(request.get("username")).orElseThrow();assertThat(user.isEnabled()).isTrue();assertThat(user.getRoles()).containsExactly("CUSTOMER");
        assertThat(user.getPasswordHash()).startsWith("$2").doesNotContain(request.get("password"));
        assertThat(bankBody.get()).doesNotContain("password","username","balance");assertThat(customerBody.get()).contains(account).doesNotContain("password");
        assertThat(http.postForEntity("/api/v1/auth/register",request,Map.class).getBody().get("accountNumber")).isEqualTo(account);
        assertThat(http.postForEntity("/api/v1/auth/login",Map.of("username",request.get("username"),"password",request.get("password")),Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        var takeover=new HashMap<>(request);takeover.put("password","Other-password-123");assertThat(http.postForEntity("/api/v1/auth/register",takeover,Map.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var duplicateEmail=new HashMap<>(request);duplicateEmail.put("username",request.get("username")+"x");assertThat(http.postForEntity("/api/v1/auth/register",duplicateEmail,Map.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test void partialProvisioningCannotLoginAndRetryReusesReservedAccount(){
        var request=request();failBank.set(true);
        try{
            var response=http.postForEntity("/api/v1/auth/register",request,Map.class);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().get("code")).isEqualTo("REGISTRATION_PENDING");
            String account=registrations.findById(request.get("username")).orElseThrow().getAccountNumber();
            assertThat(users.findById(request.get("username")).orElseThrow().isEnabled()).isFalse();
            assertThat(http.postForEntity("/api/v1/auth/login",Map.of("username",request.get("username"),"password",request.get("password")),Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            failBank.set(false);
            assertThat(http.postForEntity("/api/v1/auth/register",request,Map.class).getBody().get("accountNumber")).isEqualTo(account);
        }finally{failBank.set(false);}
    }
    @Test void shortPasswordsAreRejectedBeforeProvisioning(){var request=new HashMap<>(request());request.put("password","short");assertThat(http.postForEntity("/api/v1/auth/register",request,Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);assertThat(users.existsById(request.get("username"))).isFalse();}
    @org.junit.jupiter.api.AfterAll static void stopBackend(){backend.stop(0);}
}
