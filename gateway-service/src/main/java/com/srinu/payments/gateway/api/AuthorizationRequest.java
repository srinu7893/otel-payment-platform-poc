package com.srinu.payments.gateway.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record AuthorizationRequest(
        @NotNull UUID paymentId,
        @NotBlank String accountNumber,
        @NotNull @DecimalMin("0.01") BigDecimal amount) {
}
