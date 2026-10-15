package com.srinu.payments.payment.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TransferRequest(
    @NotBlank String idempotencyKey,
    @NotBlank String senderAccount,
    @NotBlank String receiverAccount,
    @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,
    @NotBlank @Pattern(regexp = "INR", message = "Only INR transfers are supported") String currency
) {}
