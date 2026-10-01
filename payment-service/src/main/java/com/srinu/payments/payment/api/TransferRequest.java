package com.srinu.payments.payment.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TransferRequest(
    @NotBlank String idempotencyKey,
    @NotBlank String senderAccount,
    @NotBlank String receiverAccount,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency
) {}
