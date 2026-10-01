package com.srinu.payments.notification.messaging;

import com.srinu.payments.notification.domain.NotificationRecord;
import com.srinu.payments.notification.repository.NotificationRepository;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal; import java.util.Map; import java.util.UUID;

@Component
public class PaymentEventListener {
 private static final Logger log=LoggerFactory.getLogger(PaymentEventListener.class);
 private final NotificationRepository repo;
 public PaymentEventListener(NotificationRepository repo){this.repo=repo;}
 @RabbitListener(queues="payments.notification")
 @Transactional
 public void onPaymentCompleted(Map<String,Object> event){
   UUID paymentId=UUID.fromString(String.valueOf(event.get("paymentId")));
   log.info("event=NOTIFICATION_RECEIVED paymentId={} payload={}",paymentId,event);
   var record=repo.save(new NotificationRecord(paymentId));
   record.markSent(); repo.save(record);
   log.info("event=NOTIFICATION_SENT paymentId={} notificationId={} channel={}",paymentId,record.getId(),record.getChannel());
 }
}
