package com.srinu.payments.payment.domain;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    RECONCILIATION_REQUIRED,
    COMPLETED,
    DECLINED,
    FAILED,
    CANCELLED
}
