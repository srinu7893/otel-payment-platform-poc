package com.srinu.payments.bank.api;

import java.util.UUID;

public record DebitResponse(UUID transactionId, String status, String message) {
}
