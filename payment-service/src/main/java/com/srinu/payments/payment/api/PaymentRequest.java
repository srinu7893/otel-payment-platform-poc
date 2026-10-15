package com.srinu.payments.payment.api;
import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record PaymentRequest(@NotBlank @Size(max=100) String idempotencyKey,@NotBlank @Size(max=64) String accountNumber,@NotBlank @Size(max=120) String merchant,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount) {}
