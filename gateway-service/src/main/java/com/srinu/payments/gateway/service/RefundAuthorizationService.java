package com.srinu.payments.gateway.service;

import com.srinu.payments.gateway.api.RefundAuthorizationRequest;
import com.srinu.payments.gateway.api.RefundAuthorizationResponse;
import com.srinu.payments.gateway.client.BankClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class RefundAuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(RefundAuthorizationService.class);
    private final BankClient bankClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public RefundAuthorizationService(BankClient bankClient, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.bankClient = bankClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public RefundAuthorizationResponse authorize(RefundAuthorizationRequest request) {
        long started = System.currentTimeMillis();
        log.info("event=REFUND_GATEWAY_STARTED refundId={} originalPaymentId={} account={} amount={}",
            request.refundId(), request.originalPaymentId(), mask(request.accountNumber()), request.amount());

        return circuitBreakerFactory.create("bankRefund").run(
            () -> callBank(request, started),
            throwable -> {
                log.error("event=REFUND_GATEWAY_CIRCUIT_FALLBACK refundId={} durationMs={} error={}",
                    request.refundId(), System.currentTimeMillis() - started, safeMessage(throwable));
                return new RefundAuthorizationResponse(null, "UNKNOWN", "Bank refund outcome requires reconciliation");
            });
    }

    private RefundAuthorizationResponse callBank(RefundAuthorizationRequest request, long started) {
        try {
            var bank = bankClient.refund(request.refundId(), request.originalPaymentId(),
                request.accountNumber(), request.amount());
            log.info("event=REFUND_GATEWAY_COMPLETED refundId={} transactionId={} status={} durationMs={}",
                request.refundId(), bank.transactionId(), bank.status(), System.currentTimeMillis() - started);
            return new RefundAuthorizationResponse(bank.transactionId(), bank.status(), bank.message());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                log.warn("event=REFUND_GATEWAY_BANK_REJECTED refundId={} status={} durationMs={}",
                    request.refundId(), ex.getStatusCode(), System.currentTimeMillis() - started);
                return new RefundAuthorizationResponse(null, "FAILED", "Bank rejected refund");
            }
            throw ex;
        }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.isBlank()
            ? throwable == null ? "unknown" : throwable.getClass().getSimpleName()
            : message;
    }

    private String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}
