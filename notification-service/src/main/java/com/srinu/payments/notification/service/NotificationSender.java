package com.srinu.payments.notification.service;

public interface NotificationSender {
    String channel();
    void send(String destination, String subject, String message);
}
