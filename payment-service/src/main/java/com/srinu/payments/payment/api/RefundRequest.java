package com.srinu.payments.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundRequest(
    @NotBlank @Size(max = 100) String idempotencyKey
) {}
