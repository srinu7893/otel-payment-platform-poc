package com.srinu.payments.payment.service;
import com.srinu.payments.payment.domain.MoneyRequest;
import com.srinu.payments.payment.repository.MoneyRequestRepository;
import com.srinu.payments.payment.repository.TransferRepository;
import com.srinu.payments.payment.api.TransferRequest;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Service public class MoneyRequestService {
    private final MoneyRequestRepository requests;
    private final WalletService wallet;
    private final TransferApplicationService transfers;
    private final TransferRepository transferRecords;
    public MoneyRequestService(MoneyRequestRepository requests,WalletService wallet,TransferApplicationService transfers,TransferRepository transferRecords){this.requests=requests;this.wallet=wallet;this.transfers=transfers;this.transferRecords=transferRecords;}
    public MoneyRequest create(String customer,UUID key,String payer,BigDecimal amount,String note){
        var own=wallet.customer(customer);
        UUID id=UUID.nameUUIDFromBytes((customer+":"+key).getBytes(StandardCharsets.UTF_8));
        var existing=requests.findById(id);
        if(existing.isPresent()) {
            var r=existing.get();
            if(!r.getPayerAccount().equals(payer) || r.getAmount().compareTo(amount)!=0 || !r.getNote().equals(note))throw new IllegalArgumentException("Request key was already used for different details");
            return r;
        }
        if(own.accountNumber().equals(payer))throw new IllegalArgumentException("You cannot request money from yourself");
        wallet.recipient(payer);
        var request=new MoneyRequest(id,customer,own.accountNumber(),own.name(),payer,amount,note);
        try { return requests.saveAndFlush(request); }
        catch(org.springframework.dao.DataIntegrityViolationException race){
            var prior=requests.findById(id).orElseThrow(()->race);
            if(!prior.getPayerAccount().equals(payer) || prior.getAmount().compareTo(amount)!=0 || !prior.getNote().equals(note))throw new IllegalArgumentException("Request key was already used for different details");
            return prior;
        }
    }
    public Page<MoneyRequest> list(String customer,int page,int size){
        String account=wallet.customer(customer).accountNumber();
        var result=requests.findByRequesterCustomerIdOrPayerAccountOrderByCreatedAtDesc(customer,account,PageRequest.of(Math.max(0,page),Math.max(1,Math.min(100,size))));
        return result.map(this::refresh);
    }
    private MoneyRequest refresh(MoneyRequest request){
        if("PROCESSING".equals(request.getStatus())) transferRecords.findByIdempotencyKey("request-"+request.getId()).ifPresent(t->{
            if("COMPLETED".equals(t.getStatus())) requests.finish(request.getId(),"PAID",t.getId());
            else if("FAILED".equals(t.getStatus())) requests.finish(request.getId(),"FAILED",t.getId());
        });
        return requests.findById(request.getId()).orElseThrow();
    }
    public MoneyRequest act(String customer,UUID id,String action){
        var request=requests.findById(id).orElseThrow(()->new IllegalArgumentException("Money request not found"));
        var own=wallet.customer(customer);
        if("cancel".equals(action)) {
            if(!request.getRequesterCustomerId().equals(customer))throw new org.springframework.security.access.AccessDeniedException("Only the requester can cancel");
            requests.transition(id,"OPEN","CANCELLED");return requests.findById(id).orElseThrow();
        }
        if(!request.getPayerAccount().equals(own.accountNumber()))throw new org.springframework.security.access.AccessDeniedException("Only the requested payer can respond");
        if("decline".equals(action)){requests.transition(id,"OPEN","DECLINED");return requests.findById(id).orElseThrow();}
        if(!"pay".equals(action))throw new IllegalArgumentException("Unknown request action");
        if(!"OPEN".equals(request.getStatus()) && !"PROCESSING".equals(request.getStatus()))return refresh(request);
        // Commit the claim before money movement; deterministic transfer key makes retries safe.
        if("OPEN".equals(request.getStatus()) && requests.transition(id,"OPEN","PROCESSING")==0)return refresh(request);
        final com.srinu.payments.payment.api.TransferResponse result;
        try {
            result=transfers.create(customer,new TransferRequest("request-"+id,own.accountNumber(),request.getRequesterAccount(),request.getAmount(),"INR"));
        } catch(RiskLimitExceededException | TransferApplicationService.TransferAuthorizationException rejectedBeforeCreation) {
            // These validations happen before the transfer is persisted or sent to the bank.
            requests.transition(id,"PROCESSING","OPEN");
            throw rejectedBeforeCreation;
        }
        requests.finish(id,"COMPLETED".equals(result.status())?"PAID":"FAILED".equals(result.status())?"FAILED":"PROCESSING",result.transferId());
        return requests.findById(id).orElseThrow();
    }
}
