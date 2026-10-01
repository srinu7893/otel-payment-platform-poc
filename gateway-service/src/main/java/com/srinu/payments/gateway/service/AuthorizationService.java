package com.srinu.payments.gateway.service;

import com.srinu.payments.gateway.api.AuthorizationRequest;
import com.srinu.payments.gateway.api.AuthorizationResponse;
import com.srinu.payments.gateway.client.BankClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AuthorizationService {
    private final BankClient bankClient;

    public AuthorizationService(BankClient bankClient) {
        this.bankClient = bankClient;
    }

    public AuthorizationResponse authorize(AuthorizationRequest request) {
        try {
            var bank = bankClient.debit(request.paymentId(), request.accountNumber(), request.amount());
            return new AuthorizationResponse(bank.status(), bank.message());
        } catch (RestClientResponseException ex) {
            return new AuthorizationResponse("FAILED", "Bank rejected transaction");
        }
    }
}
