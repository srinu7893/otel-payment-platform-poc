package com.srinu.payments.bank.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundRequest(
    @NotNull UUID refundId,
    @NotNull UUID originalPaymentId,
    @NotBlank String accountNumber,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {}
