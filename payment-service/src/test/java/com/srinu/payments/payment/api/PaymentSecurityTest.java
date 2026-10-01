package com.srinu.payments.payment.api;

import com.srinu.payments.payment.config.SecurityConfig;
import com.srinu.payments.payment.config.SecurityErrorHandler;
import com.srinu.payments.payment.service.PaymentApplicationService;
import com.srinu.payments.payment.service.PaymentNotFoundException;
import com.srinu.payments.payment.service.RefundApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
@TestPropertySource(properties = "security.jwt.secret=01234567890123456789012345678901")
class PaymentSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PaymentApplicationService paymentService;

    @MockBean
    private RefundApplicationService refundService;

    @Test
    void paymentEndpointWithoutTokenReturnsStandard401() throws Exception {
        mvc.perform(get("/api/v1/payments")
                .header("X-Correlation-Id", "security-test-401"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
            .andExpect(jsonPath("$.path").value("/api/v1/payments"))
            .andExpect(jsonPath("$.correlationId").value("security-test-401"))
            .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    void paymentEndpointWithUnknownRoleReturnsStandard403() throws Exception {
        mvc.perform(get("/api/v1/payments")
                .header("X-Correlation-Id", "security-test-403")
                .with(jwt().jwt(token -> token
                    .claim("customer_id", "demo-customer")
                    .claim("roles", java.util.List.of("VIEWER")))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
            .andExpect(jsonPath("$.path").value("/api/v1/payments"))
            .andExpect(jsonPath("$.correlationId").value("security-test-403"));
    }

    @Test
    void missingPaymentReturnsStandard404InsteadOf500() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentService.get(eq("demo-customer"), eq(paymentId), anyBoolean()))
            .thenThrow(new PaymentNotFoundException(paymentId));

        mvc.perform(get("/api/v1/payments/{id}", paymentId)
                .header("X-Correlation-Id", "payment-missing-404")
                .with(jwt()
                    .jwt(token -> token.claim("customer_id", "demo-customer"))
                    .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
            .andExpect(jsonPath("$.path").value("/api/v1/payments/" + paymentId));
    }
}
