package com.srinu.payments.payment.service;

public class RiskLimitExceededException extends RuntimeException {
    private final String rule;

    public RiskLimitExceededException(String rule, String message) {
        super(message);
        this.rule = rule;
    }

    public String getRule() {
        return rule;
    }
}
