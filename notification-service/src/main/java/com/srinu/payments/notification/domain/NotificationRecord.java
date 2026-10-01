package com.srinu.payments.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="notifications", indexes=@Index(name="idx_notification_payment", columnList="payment_id"))
public class NotificationRecord {
 @Id private UUID id;
 @Column(name="payment_id", nullable=false) private UUID paymentId;
 @Column(nullable=false,length=30) private String status;
 @Column(nullable=false,length=40) private String channel;
 @Column(nullable=false) private Instant createdAt;
 private Instant sentAt;
 protected NotificationRecord(){}
 public NotificationRecord(UUID paymentId){this.id=UUID.randomUUID();this.paymentId=paymentId;this.status="PENDING";this.channel="EMAIL_SIMULATED";this.createdAt=Instant.now();}
 public void markSent(){this.status="SENT";this.sentAt=Instant.now();}
 public UUID getId(){return id;} public UUID getPaymentId(){return paymentId;} public String getStatus(){return status;} public String getChannel(){return channel;} public Instant getCreatedAt(){return createdAt;} public Instant getSentAt(){return sentAt;}
}
