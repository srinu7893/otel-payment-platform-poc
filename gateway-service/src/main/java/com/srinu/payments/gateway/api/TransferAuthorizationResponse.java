package com.srinu.payments.gateway.api;

import java.util.UUID;

public record TransferAuthorizationResponse(UUID bankTransactionId, String status, String message) {}
