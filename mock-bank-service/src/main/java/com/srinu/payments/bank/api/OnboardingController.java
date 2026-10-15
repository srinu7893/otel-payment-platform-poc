package com.srinu.payments.bank.api;
import com.srinu.payments.bank.repository.AccountRepository;
import com.srinu.payments.bank.domain.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@RestController @RequestMapping("/internal/onboarding/accounts")
public class OnboardingController {
    private final AccountRepository accounts;
    public OnboardingController(AccountRepository accounts){this.accounts=accounts;}
    public record Request(@NotBlank @Size(max=120) String name){}
    @PutMapping("/{number:ACC[A-F0-9]{24}}") @Transactional public Object provision(@PathVariable String number,@Valid @RequestBody Request request){
        var existing=accounts.findById(number);
        if(existing.isPresent()){
            if(!existing.get().getCustomerName().equals(request.name()))throw new IllegalArgumentException("Bank provisioning details differ");
        } else accounts.saveAndFlush(new Account(number,request.name(),BigDecimal.ZERO));
        // Never overwrite or expose an existing balance during a provisioning retry.
        return java.util.Map.of("accountNumber",number,"status","ACTIVE");
    }
}
