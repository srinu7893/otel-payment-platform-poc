package com.srinu.payments.bank.api;

import java.util.UUID;

public record RefundResponse(UUID transactionId, String status, String message) {}
