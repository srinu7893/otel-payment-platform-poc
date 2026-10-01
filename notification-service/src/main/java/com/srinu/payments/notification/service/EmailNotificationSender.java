package com.srinu.payments.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);

    @Override
    public String channel() {
        return "EMAIL_SIMULATED";
    }

    @Override
    public void send(String destination, String subject, String message) {
        log.info("event=EMAIL_SIMULATED destination={} subject={} message={}", destination, subject, message);
    }
}
