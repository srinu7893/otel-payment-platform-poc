package com.srinu.otelpoc.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    SecretKey jwtSecretKey(@Value("${security.jwt.secret}") String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(SecretKey key) {
        return NimbusReactiveJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http, SecurityErrorHandler securityErrors) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchange -> exchange
                .pathMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/telemetry/rum").permitAll()
                .pathMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/actuator/health/**", "/actuator/info", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/openapi.yaml", "/v3/api-docs/**").permitAll()
                .pathMatchers("/api/v1/ops/**").hasAnyRole("SUPPORT", "ADMIN")
                .pathMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/customers/**").hasRole("ADMIN")
                .pathMatchers(org.springframework.http.HttpMethod.PUT,"/api/v1/customers/**").hasRole("ADMIN")
                .pathMatchers(org.springframework.http.HttpMethod.PATCH,"/api/v1/customers/**").hasRole("ADMIN")
                .pathMatchers(org.springframework.http.HttpMethod.DELETE,"/api/v1/customers/**").hasRole("ADMIN")
                .pathMatchers("/api/v1/customers/**").access(this::customerAccess)
                .pathMatchers("/api/v1/payments/**", "/api/v1/transfers/**", "/api/v1/notifications/**", "/api/v1/wallet/**", "/api/v1/money-requests/**").authenticated()
                .anyExchange().denyAll())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(securityErrors)
                .accessDeniedHandler(securityErrors))
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(grantedAuthoritiesExtractor())))
            .build();
    }

    private Mono<org.springframework.security.authorization.AuthorizationDecision> customerAccess(
            Mono<org.springframework.security.core.Authentication> authentication,
            org.springframework.security.web.server.authorization.AuthorizationContext context) {
        String path=context.getExchange().getRequest().getPath().value();
        return authentication.map(auth -> {
            boolean privileged=auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPPORT"));
            boolean own=auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt
                && path.equals("/api/v1/customers/"+jwt.getToken().getClaimAsString("customer_id"));
            return new org.springframework.security.authorization.AuthorizationDecision(privileged || own);
        }).defaultIfEmpty(new org.springframework.security.authorization.AuthorizationDecision(false));
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(authorities);
        return jwt -> Mono.just(delegate.convert(jwt));
    }
}
