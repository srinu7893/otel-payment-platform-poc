package com.srinu.payments.payment.service;
import com.srinu.payments.payment.api.*; import com.srinu.payments.payment.client.GatewayClient; import com.srinu.payments.payment.domain.Payment; import com.srinu.payments.payment.messaging.PaymentEventPublisher; import com.srinu.payments.payment.repository.PaymentRepository; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.UUID;
@Service public class PaymentApplicationService {
 private final PaymentRepository repo; private final GatewayClient gateway; private final PaymentEventPublisher events;
 public PaymentApplicationService(PaymentRepository r,GatewayClient g,PaymentEventPublisher e){repo=r;gateway=g;events=e;}
 @Transactional public PaymentResponse pay(PaymentRequest req){
  var existing=repo.findByIdempotencyKey(req.idempotencyKey()); if(existing.isPresent()){var p=existing.get();return new PaymentResponse(p.getId(),p.getStatus(),p.getAmount(),"Idempotent replay");}
  var p=repo.save(new Payment(UUID.randomUUID(),req.idempotencyKey(),req.accountNumber(),req.merchant(),req.amount(),"PENDING"));
  var result=gateway.authorize(p.getId(),req.accountNumber(),req.amount()); p.setStatus(result.status()); repo.save(p);
  if("COMPLETED".equals(result.status())) events.completed(p.getId(),p.getAmount());
  return new PaymentResponse(p.getId(),p.getStatus(),p.getAmount(),result.message());
 }
}