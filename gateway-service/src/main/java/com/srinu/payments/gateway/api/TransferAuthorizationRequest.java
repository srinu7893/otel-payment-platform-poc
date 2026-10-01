package com.srinu.payments.gateway.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferAuthorizationRequest(
    @NotNull UUID paymentId,
    @NotBlank String senderAccount,
    @NotBlank String receiverAccount,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency
) {}
