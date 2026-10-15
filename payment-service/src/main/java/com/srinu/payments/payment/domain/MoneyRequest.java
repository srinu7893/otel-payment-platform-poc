package com.srinu.payments.payment.domain;
import jakarta.persistence.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="money_request")
public class MoneyRequest {
    @Id private UUID id;
    @Column(nullable=false,length=80) private String requesterCustomerId;
    @Column(nullable=false,length=64) private String requesterAccount;
    @Column(nullable=false,length=160) private String requesterName;
    @Column(nullable=false,length=64) private String payerAccount;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
    @Column(nullable=false,length=200) private String note;
    @Column(nullable=false,length=30) private String status;
    @Column(nullable=false) private Instant createdAt;
    private UUID transferId;
    protected MoneyRequest(){}
    public MoneyRequest(UUID id,String customer,String account,String name,String payer,BigDecimal amount,String note){
        this.id=id;requesterCustomerId=customer;requesterAccount=account;requesterName=name;payerAccount=payer;this.amount=amount;this.note=note;status="OPEN";createdAt=Instant.now();
    }
    public UUID getId(){return id;}
    public String getRequesterCustomerId(){return requesterCustomerId;}
    public String getRequesterAccount(){return requesterAccount;}
    public String getRequesterName(){return requesterName;}
    public String getPayerAccount(){return payerAccount;}
    public BigDecimal getAmount(){return amount;}
    public String getNote(){return note;}
    public String getStatus(){return status;}
    public Instant getCreatedAt(){return createdAt;}
    public UUID getTransferId(){return transferId;}
}
