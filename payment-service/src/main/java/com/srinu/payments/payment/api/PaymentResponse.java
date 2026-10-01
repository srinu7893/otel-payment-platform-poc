package com.srinu.payments.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID paymentId,
    String status,
    BigDecimal amount,
    String merchant,
    String account,
    String failureCode,
    String message,
    Instant createdAt
) {}
