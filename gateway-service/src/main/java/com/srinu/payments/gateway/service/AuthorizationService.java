package com.srinu.payments.gateway.service;

import com.srinu.payments.gateway.api.AuthorizationRequest;
import com.srinu.payments.gateway.api.AuthorizationResponse;
import com.srinu.payments.gateway.client.BankClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);
    private final BankClient bankClient;

    public AuthorizationService(BankClient bankClient) {
        this.bankClient = bankClient;
    }

    public AuthorizationResponse authorize(AuthorizationRequest request) {
        long started = System.currentTimeMillis();
        log.info("event=GATEWAY_AUTH_STARTED paymentId={} account={} amount={}", request.paymentId(), request.accountNumber(), request.amount());
        try {
            var bank = bankClient.debit(request.paymentId(), request.accountNumber(), request.amount());
            log.info("event=GATEWAY_AUTH_COMPLETED paymentId={} bankStatus={} durationMs={}", request.paymentId(), bank.status(), System.currentTimeMillis()-started);
            return new AuthorizationResponse(bank.status(), bank.message());
        } catch (RestClientResponseException ex) {
            log.error("event=GATEWAY_BANK_HTTP_ERROR paymentId={} status={} durationMs={}", request.paymentId(), ex.getStatusCode(), System.currentTimeMillis()-started);
            return new AuthorizationResponse("FAILED", "Bank rejected transaction");
        } catch (RuntimeException ex) {
            log.error("event=GATEWAY_BANK_FAILURE paymentId={} durationMs={} error={}", request.paymentId(), System.currentTimeMillis()-started, ex.getMessage());
            return new AuthorizationResponse("FAILED", "Bank unavailable");
        }
    }
}
