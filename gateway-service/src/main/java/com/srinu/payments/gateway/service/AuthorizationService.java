package com.srinu.payments.gateway.service;

import com.srinu.payments.gateway.api.AuthorizationRequest;
import com.srinu.payments.gateway.api.AuthorizationResponse;
import com.srinu.payments.gateway.client.BankClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);
    private final BankClient bankClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public AuthorizationService(BankClient bankClient, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.bankClient = bankClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public AuthorizationResponse authorize(AuthorizationRequest request) {
        long started = System.currentTimeMillis();
        log.info("event=GATEWAY_AUTH_STARTED paymentId={} account={} amount={}",
            request.paymentId(), mask(request.accountNumber()), request.amount());

        return circuitBreakerFactory.create("bankDebit").run(
            () -> callBank(request, started),
            throwable -> {
                log.error("event=GATEWAY_BANK_CIRCUIT_FALLBACK paymentId={} durationMs={} error={}",
                    request.paymentId(), System.currentTimeMillis() - started, safeMessage(throwable));
                return new AuthorizationResponse("FAILED", "Bank unavailable");
            });
    }

    private AuthorizationResponse callBank(AuthorizationRequest request, long started) {
        try {
            var bank = bankClient.debit(request.paymentId(), request.accountNumber(), request.amount());
            log.info("event=GATEWAY_AUTH_COMPLETED paymentId={} bankStatus={} durationMs={}",
                request.paymentId(), bank.status(), System.currentTimeMillis() - started);
            return new AuthorizationResponse(bank.status(), bank.message());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                log.warn("event=GATEWAY_BANK_REJECTED paymentId={} status={} durationMs={}",
                    request.paymentId(), ex.getStatusCode(), System.currentTimeMillis() - started);
                return new AuthorizationResponse("FAILED", "Bank rejected transaction");
            }
            throw ex;
        }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.isBlank() ? throwable == null ? "unknown" : throwable.getClass().getSimpleName() : message;
    }

    private String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}
