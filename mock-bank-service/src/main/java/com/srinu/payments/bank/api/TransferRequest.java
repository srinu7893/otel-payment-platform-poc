package com.srinu.payments.bank.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
    @NotNull UUID paymentId,
    @NotBlank String senderAccount,
    @NotBlank String receiverAccount,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency
) {}
