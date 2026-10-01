package com.srinu.payments.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
    UUID refundId,
    UUID paymentId,
    UUID bankTransactionId,
    BigDecimal amount,
    String status,
    String failureCode,
    String message,
    Instant createdAt
) {}
