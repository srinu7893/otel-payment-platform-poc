package com.srinu.payments.payment.api;
import java.math.BigDecimal; import java.util.UUID;
public record PaymentResponse(UUID paymentId,String status,BigDecimal amount,String message) {}