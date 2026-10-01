package com.srinu.payments.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(SmsNotificationSender.class);

    @Override
    public String channel() {
        return "SMS_SIMULATED";
    }

    @Override
    public void send(String destination, String subject, String message) {
        log.info("event=SMS_SIMULATED destination={} message={}", mask(destination), message);
    }

    private String mask(String mobile) {
        if (mobile == null || mobile.length() <= 4) return "****";
        return "******" + mobile.substring(mobile.length() - 4);
    }
}
