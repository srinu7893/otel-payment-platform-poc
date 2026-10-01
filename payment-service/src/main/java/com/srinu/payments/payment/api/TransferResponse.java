package com.srinu.payments.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
    UUID transferId,
    UUID bankTransactionId,
    String senderAccount,
    String receiverAccount,
    BigDecimal amount,
    String currency,
    String status,
    String message,
    Instant createdAt
) {}
