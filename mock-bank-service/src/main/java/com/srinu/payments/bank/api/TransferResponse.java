package com.srinu.payments.bank.api;

import java.util.UUID;

public record TransferResponse(UUID transactionId, String status, String message) {}
