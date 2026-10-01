package com.srinu.payments.gateway.api;

import java.util.UUID;

public record RefundAuthorizationResponse(UUID bankTransactionId, String status, String message) {}
