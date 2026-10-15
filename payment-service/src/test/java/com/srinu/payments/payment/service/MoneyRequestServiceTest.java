package com.srinu.payments.payment.service;
import com.srinu.payments.payment.domain.MoneyRequest;
import com.srinu.payments.payment.repository.*;
import com.srinu.payments.payment.client.CustomerClient;
import com.srinu.payments.payment.api.TransferResponse;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class MoneyRequestServiceTest {
    MoneyRequestRepository repo=mock(MoneyRequestRepository.class);
    WalletService wallet=mock(WalletService.class);
    TransferApplicationService transfers=mock(TransferApplicationService.class);
    TransferRepository records=mock(TransferRepository.class);
    MoneyRequestService service=new MoneyRequestService(repo,wallet,transfers,records);
    UUID id=UUID.randomUUID();
    MoneyRequest request=new MoneyRequest(id,"requester","ACC1001","Demo","ACC2001",new BigDecimal("5.00"),"Lunch");
    @Test void creationDoesNotMoveMoney() {
        when(wallet.customer("requester")).thenReturn(new CustomerClient.CustomerView("requester","Demo","demo@example.com","ACC1001",true));
        when(repo.findById(any())).thenReturn(Optional.empty());when(repo.saveAndFlush(any())).thenAnswer(c->c.getArgument(0));
        assertThat(service.create("requester",UUID.randomUUID(),"ACC2001",new BigDecimal("5.00"),"Lunch").getStatus()).isEqualTo("OPEN");
        verifyNoInteractions(transfers);
    }
    @Test void unrelatedCustomerCannotPayOrDecline() {
        when(repo.findById(id)).thenReturn(Optional.of(request));when(wallet.customer("other")).thenReturn(new CustomerClient.CustomerView("other","Other","o@example.com","ACC3001",true));
        for(String action:List.of("pay","decline","cancel"))assertThatThrownBy(()->service.act("other",id,action)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(transfers);
    }
    @Test void payerApprovalUsesDeterministicTransferKey() {
        when(repo.findById(id)).thenReturn(Optional.of(request));when(wallet.customer("payer")).thenReturn(new CustomerClient.CustomerView("payer","Receiver","r@example.com","ACC2001",true));when(repo.transition(id,"OPEN","PROCESSING")).thenReturn(1);
        UUID transfer=UUID.randomUUID();when(transfers.create(eq("payer"),any())).thenReturn(new TransferResponse(transfer,null,"****2001","****1001",new BigDecimal("5.00"),"INR","COMPLETED","ok",java.time.Instant.now()));
        service.act("payer",id,"pay");
        verify(transfers).create(eq("payer"),argThat(r->r.idempotencyKey().equals("request-"+id) && r.senderAccount().equals("ACC2001") && r.receiverAccount().equals("ACC1001")));
        verify(repo).finish(id,"PAID",transfer);
    }
    @Test void requesterCanCancelWithoutMovingMoney() {
        when(repo.findById(id)).thenReturn(Optional.of(request));when(wallet.customer("requester")).thenReturn(new CustomerClient.CustomerView("requester","Demo","d@example.com","ACC1001",true));
        service.act("requester",id,"cancel");verify(repo).transition(id,"OPEN","CANCELLED");verifyNoInteractions(transfers);
    }
    @Test void riskRejectionReopensRequestBeforeAnyMoneyMovement() {
        when(repo.findById(id)).thenReturn(Optional.of(request));when(wallet.customer("payer")).thenReturn(new CustomerClient.CustomerView("payer","Receiver","r@example.com","ACC2001",true));when(repo.transition(id,"OPEN","PROCESSING")).thenReturn(1);
        when(transfers.create(eq("payer"),any())).thenThrow(new TransferApplicationService.TransferAuthorizationException("Customer inactive"));
        assertThatThrownBy(()->service.act("payer",id,"pay")).isInstanceOf(TransferApplicationService.TransferAuthorizationException.class);
        verify(repo).transition(id,"PROCESSING","OPEN");verify(repo,never()).finish(any(),any(),any());
    }
}
