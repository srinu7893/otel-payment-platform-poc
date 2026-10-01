package com.srinu.payments.payment.domain;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="payment",uniqueConstraints=@UniqueConstraint(columnNames="idempotencyKey"))
public class Payment {
 @Id private UUID id; @Column(nullable=false) private String idempotencyKey; @Column(nullable=false) private String accountNumber;
 @Column(nullable=false) private String merchant; @Column(nullable=false) private BigDecimal amount; @Column(nullable=false) private String status; @Column(nullable=false) private Instant createdAt;
 protected Payment(){} public Payment(UUID id,String key,String acct,String merchant,BigDecimal amount,String status){this.id=id;this.idempotencyKey=key;this.accountNumber=acct;this.merchant=merchant;this.amount=amount;this.status=status;this.createdAt=Instant.now();}
 public UUID getId(){return id;} public String getIdempotencyKey(){return idempotencyKey;} public BigDecimal getAmount(){return amount;} public String getStatus(){return status;} public void setStatus(String s){status=s;}
}