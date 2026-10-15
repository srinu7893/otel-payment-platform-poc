package com.srinu.payments.payment.api;
import com.srinu.payments.payment.config.*;
import com.srinu.payments.payment.service.WalletService;
import com.srinu.payments.payment.service.MoneyRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest({WalletController.class,MoneyRequestController.class})
@Import({SecurityConfig.class,SecurityErrorHandler.class})
@TestPropertySource(properties="security.jwt.secret=01234567890123456789012345678901")
class WalletSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean WalletService wallet;
    @MockitoBean MoneyRequestService requests;
    private org.springframework.test.web.servlet.request.RequestPostProcessor customer(){return jwt().jwt(j->j.claim("customer_id","demo-customer")).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));}
    @Test void walletRequiresAuthentication() throws Exception {mvc.perform(get("/api/v1/wallet")).andExpect(status().isUnauthorized());verifyNoInteractions(wallet);}
    @Test void walletAlwaysUsesJwtIdentity() throws Exception {
        mvc.perform(get("/api/v1/wallet?customerId=another").with(customer())).andExpect(status().isOk());verify(wallet).balance("demo-customer");
    }
    @Test void privilegedRoleCannotDepositOnBehalfOfCustomers() throws Exception {
        mvc.perform(post("/api/v1/wallet/deposits").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))).contentType("application/json").content("{\"idempotencyKey\":\""+java.util.UUID.randomUUID()+"\",\"amount\":10}")).andExpect(status().isForbidden());verifyNoInteractions(wallet);
    }
    @Test void depositsRejectInvalidMoneyPrecision() throws Exception {
        mvc.perform(post("/api/v1/wallet/deposits").with(customer()).contentType("application/json").content("{\"idempotencyKey\":\""+java.util.UUID.randomUUID()+"\",\"amount\":0.001}")).andExpect(status().isBadRequest());verifyNoInteractions(wallet);
    }
    @Test void unauthorizedRequestActionReturns403() throws Exception {
        var id=java.util.UUID.randomUUID();when(requests.act("demo-customer",id,"pay")).thenThrow(new org.springframework.security.access.AccessDeniedException("private details"));
        mvc.perform(post("/api/v1/money-requests/"+id+"/pay").with(customer())).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("This action is not permitted for your account"));
    }
}
