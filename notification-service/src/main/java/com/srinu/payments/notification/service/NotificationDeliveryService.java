package com.srinu.payments.notification.service;

import com.srinu.payments.notification.domain.NotificationRecord;
import com.srinu.payments.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private final NotificationRepository repository;
    private final Map<String, NotificationSender> senders;

    public NotificationDeliveryService(NotificationRepository repository, java.util.List<NotificationSender> senders) {
        this.repository = repository;
        this.senders = senders.stream().collect(Collectors.toMap(NotificationSender::channel, Function.identity()));
    }

    @Transactional
    public NotificationRecord deliver(NotificationRecord record, String subject, String message) {
        NotificationSender sender = senders.get(record.getChannel());
        if (sender == null) throw new IllegalStateException("No sender registered for channel " + record.getChannel());

        record.markProcessing();
        repository.save(record);
        try {
            sender.send(record.getDestination(), subject, message);
            record.markSent();
            repository.save(record);
            log.info("event=NOTIFICATION_SENT notificationId={} channel={} attempts={} eventType={}",
                record.getId(), record.getChannel(), record.getAttempts(), record.getEventType());
            return record;
        } catch (RuntimeException ex) {
            record.markFailed(ex.getMessage());
            repository.save(record);
            log.error("event=NOTIFICATION_FAILED notificationId={} channel={} attempts={} error={}",
                record.getId(), record.getChannel(), record.getAttempts(), ex.getMessage());
            throw ex;
        }
    }
}
