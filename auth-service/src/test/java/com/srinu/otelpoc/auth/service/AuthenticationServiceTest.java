package com.srinu.otelpoc.auth.service;

import com.srinu.otelpoc.auth.domain.AppUser;
import com.srinu.otelpoc.auth.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthenticationServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtEncoder jwtEncoder = mock(JwtEncoder.class);
    private final AuthenticationService service = new AuthenticationService(users, passwords, jwtEncoder);

    @Test
    void loginReturnsBearerTokenAndRecordsLogin() {
        var user = new AppUser("demo", "hash", "demo-customer", Set.of("CUSTOMER"));
        when(users.findById("demo")).thenReturn(Optional.of(user));
        when(passwords.matches("demo123", "hash")).thenReturn(true);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt("signed-token"));

        var result = service.login("demo", "demo123");

        assertThat(result.accessToken()).isEqualTo("signed-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresInSeconds()).isEqualTo(1800);
        assertThat(result.customerId()).isEqualTo("demo-customer");
        assertThat(result.roles()).containsExactly("CUSTOMER");
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(users).save(user);
        verify(jwtEncoder).encode(any(JwtEncoderParameters.class));
    }

    @Test
    void loginRejectsBadPasswordWithoutIssuingToken() {
        var user = new AppUser("demo", "hash", "demo-customer", Set.of("CUSTOMER"));
        when(users.findById("demo")).thenReturn(Optional.of(user));
        when(passwords.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("demo", "wrong"))
            .isInstanceOf(AuthenticationService.InvalidCredentialsException.class);

        verifyNoInteractions(jwtEncoder);
        verify(users, never()).save(any());
    }

    @Test
    void loginRejectsLockedUser() {
        var user = new AppUser("demo", "hash", "demo-customer", Set.of("CUSTOMER"));
        user.lock();
        when(users.findById("demo")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("demo", "demo123"))
            .isInstanceOf(AuthenticationService.InvalidCredentialsException.class);

        verifyNoInteractions(passwords, jwtEncoder);
        verify(users, never()).save(any());
    }

    private Jwt jwt(String tokenValue) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(tokenValue)
            .header("alg", "HS256")
            .subject("demo")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(1800))
            .build();
    }
}
