package com.srinu.payments.gateway.api;

import java.util.UUID;

public record AuthorizationResponse(UUID bankTransactionId, String status, String message) {
}
