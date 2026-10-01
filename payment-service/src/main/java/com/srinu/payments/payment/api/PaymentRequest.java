package com.srinu.payments.payment.api;
import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record PaymentRequest(@NotBlank String idempotencyKey,@NotBlank String accountNumber,@NotBlank String merchant,@NotNull @DecimalMin("0.01") BigDecimal amount) {}