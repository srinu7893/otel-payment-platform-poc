package com.srinu.payments.gateway.service;

import com.srinu.payments.gateway.api.TransferAuthorizationRequest;
import com.srinu.payments.gateway.api.TransferAuthorizationResponse;
import com.srinu.payments.gateway.client.BankClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class TransferAuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(TransferAuthorizationService.class);
    private final BankClient bankClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public TransferAuthorizationService(BankClient bankClient, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.bankClient = bankClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public TransferAuthorizationResponse authorize(TransferAuthorizationRequest request) {
        long started = System.currentTimeMillis();
        log.info("event=TRANSFER_GATEWAY_STARTED paymentId={} sender={} receiver={} amount={} currency={}",
            request.paymentId(), mask(request.senderAccount()), mask(request.receiverAccount()), request.amount(), request.currency());

        return circuitBreakerFactory.create("bankTransfer").run(
            () -> callBank(request, started),
            throwable -> {
                log.error("event=TRANSFER_GATEWAY_CIRCUIT_FALLBACK paymentId={} durationMs={} error={}",
                    request.paymentId(), System.currentTimeMillis() - started, safeMessage(throwable));
                return new TransferAuthorizationResponse(null, "FAILED", "Bank unavailable");
            });
    }

    private TransferAuthorizationResponse callBank(TransferAuthorizationRequest request, long started) {
        try {
            var bank = bankClient.transfer(request.paymentId(), request.senderAccount(), request.receiverAccount(),
                request.amount(), request.currency());
            log.info("event=TRANSFER_GATEWAY_COMPLETED paymentId={} transactionId={} status={} durationMs={}",
                request.paymentId(), bank.transactionId(), bank.status(), System.currentTimeMillis() - started);
            return new TransferAuthorizationResponse(bank.transactionId(), bank.status(), bank.message());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                log.warn("event=TRANSFER_GATEWAY_BANK_REJECTED paymentId={} status={} durationMs={}",
                    request.paymentId(), ex.getStatusCode(), System.currentTimeMillis() - started);
                return new TransferAuthorizationResponse(null, "FAILED", "Bank rejected transfer");
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
