package com.srinu.payments.bank.api;

import com.srinu.payments.bank.domain.Account;
import com.srinu.payments.bank.service.AccountAdminService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AccountAdminController {
 private final AccountAdminService service;
 public AccountAdminController(AccountAdminService service){this.service=service;}

 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public Account create(@Valid @RequestBody AccountAdminRequest request){return service.create(request);}

 @GetMapping("/{accountNumber}")
 public Account get(@PathVariable String accountNumber){return service.get(accountNumber);}

 @GetMapping
 public Page<Account> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(page,size);}

 @PutMapping("/{accountNumber}")
 public Account update(@PathVariable String accountNumber,@Valid @RequestBody AccountAdminRequest request){return service.update(accountNumber,request);}

 @PostMapping("/{accountNumber}/deactivate")
 public Account deactivate(@PathVariable String accountNumber){return service.deactivate(accountNumber);}

 @PostMapping("/{accountNumber}/activate")
 public Account activate(@PathVariable String accountNumber){return service.activate(accountNumber);}
}
