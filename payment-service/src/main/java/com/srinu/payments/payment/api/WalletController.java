package com.srinu.payments.payment.api;
import com.srinu.payments.payment.client.WalletClient;
import com.srinu.payments.payment.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/wallet") @PreAuthorize("hasRole('CUSTOMER')")
public class WalletController {
    private final WalletService service;
    public WalletController(WalletService service){this.service=service;}
    public record DepositRequest(@NotNull UUID idempotencyKey,@NotNull @DecimalMin("0.01") @DecimalMax("100000") @Digits(integer=6,fraction=2) BigDecimal amount) {}
    static String customer(Jwt jwt){String id=jwt.getClaimAsString("customer_id");if(id==null || id.isBlank())throw new IllegalArgumentException("Missing customer identity");return id;}
    @GetMapping public WalletClient.Balance balance(@AuthenticationPrincipal Jwt jwt){return service.balance(customer(jwt));}
    @GetMapping("/recipients/{account}") public WalletClient.Recipient recipient(@PathVariable String account){return service.recipient(account);}
    @GetMapping("/activity") public List<WalletClient.Activity> activity(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.activity(customer(jwt),page,size);}
    @PostMapping("/deposits") public WalletClient.Deposit deposit(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody DepositRequest request){return service.deposit(customer(jwt),new WalletClient.DepositRequest(request.idempotencyKey(),request.amount()));}
}
