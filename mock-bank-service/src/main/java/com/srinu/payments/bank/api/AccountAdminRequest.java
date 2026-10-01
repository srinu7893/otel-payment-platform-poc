package com.srinu.payments.bank.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AccountAdminRequest(
        @NotBlank String accountNumber,
        @NotBlank String customerName,
        @NotNull @DecimalMin("0.00") BigDecimal balance) {}
