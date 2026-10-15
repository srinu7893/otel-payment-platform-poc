package com.srinu.payments.gateway.api;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class WalletUnavailableTest {
    @Test void unavailableBankReturns503InsteadOfGeneric500() throws Exception {
        var socket=new java.net.ServerSocket(0,0,java.net.InetAddress.getLoopbackAddress());int port=socket.getLocalPort();socket.close();
        var controller=new WalletController(RestClient.builder(),"http://127.0.0.1:"+port,java.time.Duration.ofMillis(200));
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build()
            .perform(get("/api/v1/wallet/ACC1001"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("BACKEND_UNAVAILABLE"));
    }
}
