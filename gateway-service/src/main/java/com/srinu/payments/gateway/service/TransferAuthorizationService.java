package com.srinu.payments.gateway.service;

import com.srinu.payments.gateway.api.TransferAuthorizationRequest;
import com.srinu.payments.gateway.api.TransferAuthorizationResponse;
import com.srinu.payments.gateway.client.BankClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class TransferAuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(TransferAuthorizationService.class);
    private final BankClient bankClient;

    public TransferAuthorizationService(BankClient bankClient) {
        this.bankClient = bankClient;
    }

    public TransferAuthorizationResponse authorize(TransferAuthorizationRequest request) {
        long started = System.currentTimeMillis();
        log.info("event=TRANSFER_GATEWAY_STARTED paymentId={} sender={} receiver={} amount={} currency={}",
            request.paymentId(), mask(request.senderAccount()), mask(request.receiverAccount()), request.amount(), request.currency());
        try {
            var bank = bankClient.transfer(request.paymentId(), request.senderAccount(), request.receiverAccount(),
                request.amount(), request.currency());
            log.info("event=TRANSFER_GATEWAY_COMPLETED paymentId={} transactionId={} status={} durationMs={}",
                request.paymentId(), bank.transactionId(), bank.status(), System.currentTimeMillis() - started);
            return new TransferAuthorizationResponse(bank.transactionId(), bank.status(), bank.message());
        } catch (RestClientResponseException ex) {
            log.error("event=TRANSFER_GATEWAY_BANK_HTTP_ERROR paymentId={} status={} durationMs={}",
                request.paymentId(), ex.getStatusCode(), System.currentTimeMillis() - started);
            return new TransferAuthorizationResponse(null, "FAILED", "Bank rejected transfer");
        } catch (RuntimeException ex) {
            log.error("event=TRANSFER_GATEWAY_BANK_FAILURE paymentId={} durationMs={} error={}",
                request.paymentId(), System.currentTimeMillis() - started, ex.getMessage());
            return new TransferAuthorizationResponse(null, "FAILED", "Bank unavailable");
        }
    }

    private String mask(String account) {
        if (account == null || account.length() <= 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}
