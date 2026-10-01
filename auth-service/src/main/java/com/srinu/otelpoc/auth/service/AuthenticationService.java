package com.srinu.otelpoc.auth.service;

import com.srinu.otelpoc.auth.domain.AppUser;
import com.srinu.otelpoc.auth.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AuthenticationService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    @Transactional
    public LoginResult login(String username, String password) {
        AppUser user = users.findById(username).orElse(null);
        if (user == null || !user.isEnabled() || user.isLocked() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("event=LOGIN_FAILED username={}", username);
            throw new InvalidCredentialsException();
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(30, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("otel-payment-auth")
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(user.getUsername())
            .claim("customer_id", user.getCustomerId())
            .claim("roles", user.getRoles())
            .build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
        user.recordLogin();
        users.save(user);
        log.info("event=LOGIN_SUCCEEDED username={} customerId={} roles={}", user.getUsername(), user.getCustomerId(), user.getRoles());
        return new LoginResult(token, "Bearer", 1800, user.getCustomerId(), user.getRoles());
    }

    public record LoginResult(String accessToken, String tokenType, long expiresInSeconds, String customerId, java.util.Set<String> roles) {}

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() { super("Invalid credentials"); }
    }
}
