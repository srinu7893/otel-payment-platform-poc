package com.srinu.otelpoc.auth.service;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.MDC;
import java.util.Map;

@Service public class RegistrationService {
    private final RegistrationState state;
    private final AuthenticationService authentication;
    private final RestClient customers;
    private final RestClient bank;
    public RegistrationService(RegistrationState state,AuthenticationService authentication,RestClient.Builder builder,
        @Value("${clients.customer.base-url:http://localhost:8084}") String customer,
        @Value("${clients.bank.base-url:http://localhost:8082}") String bank){
        this.state=state;this.authentication=authentication;
        this.customers=builder.clone().baseUrl(customer).build();this.bank=builder.clone().baseUrl(bank).build();
    }
    public record Result(AuthenticationService.LoginResult session,String accountNumber,String status){}
    public Result register(String username,String password,String name,String email){
        username=username.trim().toLowerCase(java.util.Locale.ROOT);name=name.trim();email=email.trim().toLowerCase(java.util.Locale.ROOT);
        if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes");
        final com.srinu.otelpoc.auth.domain.Registration registration;
        try { registration=state.reserve(username,password,name,email); }
        catch(org.springframework.dao.DataIntegrityViolationException race){throw new RegistrationConflict();}
        if(!"ACTIVE".equals(registration.getState())){
            String correlation=java.util.Objects.toString(MDC.get("correlationId"),java.util.UUID.randomUUID().toString());
            try {
                customers.put().uri("/internal/onboarding/customers/{id}",state.customerId(username))
                    .header("X-Correlation-Id",correlation).body(Map.of("name",name,"email",email,"accountNumber",registration.getAccountNumber())).retrieve().toBodilessEntity();
            } catch(org.springframework.web.client.HttpClientErrorException.Conflict duplicateEmail){
                // Customer rejected the create; the bank has not been called, so the reservation can be released.
                state.discardUnprovisioned(username);throw new RegistrationConflict();
            } catch(org.springframework.web.client.RestClientException failure){throw new ProvisioningUnavailable();}
            try {
                bank.put().uri("/internal/onboarding/accounts/{account}",registration.getAccountNumber())
                    .header("X-Correlation-Id",correlation).body(Map.of("name",name)).retrieve().toBodilessEntity();
            } catch(org.springframework.web.client.RestClientException failure){throw new ProvisioningUnavailable();}
            state.activate(username);
        }
        return new Result(authentication.login(username,password),registration.getAccountNumber(),"ACTIVE");
    }
    public static class RegistrationConflict extends RuntimeException {}
    public static class ProvisioningUnavailable extends RuntimeException {}
}
