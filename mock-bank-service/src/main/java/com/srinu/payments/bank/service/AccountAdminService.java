package com.srinu.payments.bank.service;

import com.srinu.payments.bank.api.AccountAdminRequest;
import com.srinu.payments.bank.domain.Account;
import com.srinu.payments.bank.repository.AccountRepository;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page; import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAdminService {
 private static final Logger log=LoggerFactory.getLogger(AccountAdminService.class);
 private final AccountRepository repo;
 public AccountAdminService(AccountRepository repo){this.repo=repo;}

 @Transactional public Account create(AccountAdminRequest req){
   if(repo.existsById(req.accountNumber())) throw new IllegalStateException("Account already exists");
   var a=repo.save(new Account(req.accountNumber(),req.customerName(),req.balance()));
   log.info("event=BANK_ACCOUNT_CREATED accountNumber={}",a.getAccountNumber()); return a;
 }
 @Transactional(readOnly=true) public Account get(String id){return repo.findById(id).orElseThrow(()->new IllegalArgumentException("Account not found"));}
 @Transactional(readOnly=true) public Page<Account> list(int page,int size){return repo.findAll(PageRequest.of(page,Math.min(size,100)));}
 @Transactional public Account update(String id,AccountAdminRequest req){var a=get(id);a.updateProfile(req.customerName());a.setBalance(req.balance());log.info("event=BANK_ACCOUNT_UPDATED accountNumber={}",id);return repo.save(a);}
 @Transactional public Account deactivate(String id){var a=get(id);a.deactivate();log.info("event=BANK_ACCOUNT_DEACTIVATED accountNumber={}",id);return repo.save(a);}
 @Transactional public Account activate(String id){var a=get(id);a.activate();log.info("event=BANK_ACCOUNT_ACTIVATED accountNumber={}",id);return repo.save(a);}
}
