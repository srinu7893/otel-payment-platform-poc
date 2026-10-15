package com.srinu.payments.payment.api;
import com.srinu.payments.payment.domain.MoneyRequest;
import com.srinu.payments.payment.service.MoneyRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import java.util.UUID;
import java.math.BigDecimal;

@RestController @RequestMapping("/api/v1/money-requests") @PreAuthorize("hasRole('CUSTOMER')")
public class MoneyRequestController {
    private final MoneyRequestService service;
    public MoneyRequestController(MoneyRequestService service){this.service=service;}
    public record Request(@NotNull UUID idempotencyKey,@NotBlank @Pattern(regexp="[A-Za-z0-9-]{3,64}") String payerAccount,@NotNull @DecimalMin("0.01") @DecimalMax("100000") @Digits(integer=6,fraction=2) BigDecimal amount,@NotNull @Size(max=200) String note){}
    @GetMapping public Page<MoneyRequest> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(WalletController.customer(jwt),page,size);}
    @PostMapping public MoneyRequest create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Request request){return service.create(WalletController.customer(jwt),request.idempotencyKey(),request.payerAccount(),request.amount(),request.note());}
    @PostMapping("/{id}/{action:pay|decline|cancel}") public MoneyRequest act(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@PathVariable String action){return service.act(WalletController.customer(jwt),id,action);}
}
