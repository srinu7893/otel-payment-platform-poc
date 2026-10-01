package com.srinu.payments.notification.api;

import com.srinu.payments.notification.config.SecurityConfig;
import com.srinu.payments.notification.config.SecurityErrorHandler;
import com.srinu.payments.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
@TestPropertySource(properties = "security.jwt.secret=01234567890123456789012345678901")
class NotificationSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private NotificationRepository repository;

    @Test
    void notificationEndpointWithoutTokenReturnsStandard401() throws Exception {
        mvc.perform(get("/api/v1/notifications")
                .header("X-Correlation-Id", "notification-security-401"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
            .andExpect(jsonPath("$.path").value("/api/v1/notifications"))
            .andExpect(jsonPath("$.correlationId").value("notification-security-401"))
            .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    void notificationEndpointWithUnknownRoleReturnsStandard403() throws Exception {
        mvc.perform(get("/api/v1/notifications")
                .header("X-Correlation-Id", "notification-security-403")
                .with(jwt().jwt(token -> token
                    .claim("customer_id", "demo-customer")
                    .claim("roles", java.util.List.of("VIEWER")))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
            .andExpect(jsonPath("$.path").value("/api/v1/notifications"))
            .andExpect(jsonPath("$.correlationId").value("notification-security-403"));
    }
}
