package com.srinu.payments.bank.api;
import com.srinu.payments.bank.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Internal bank API; customer ownership is checked by payment-service before calling. */
@RestController @RequestMapping("/api/v1/bank/wallet")
public class WalletController {
    private final WalletService service;
    public WalletController(WalletService service){this.service=service;}
    public record DepositRequest(@NotNull UUID idempotencyKey, @NotNull @DecimalMin("0.01") @DecimalMax("100000") @Digits(integer=6,fraction=2) BigDecimal amount) {}
    @GetMapping("/{account}") public WalletService.Balance balance(@PathVariable String account){return service.balance(account);}
    @GetMapping("/recipients/{account}") public WalletService.Recipient recipient(@PathVariable String account){return service.recipient(account);}
    @GetMapping("/{account}/activity") public List<WalletService.Activity> activity(@PathVariable String account,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.activity(account,page,size);}
    @PostMapping("/{account}/deposits") public WalletService.Deposit deposit(@PathVariable String account,@Valid @RequestBody DepositRequest request){return service.deposit(account,request.idempotencyKey(),request.amount());}
}
