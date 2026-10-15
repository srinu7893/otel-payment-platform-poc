package com.srinu.otelpoc.customer.api;
import com.srinu.otelpoc.customer.domain.Customer;
import com.srinu.otelpoc.customer.repository.CustomerRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Private Cloud Run endpoint; deliberately outside every public edge route. */
@RestController @RequestMapping("/internal/onboarding/customers")
public class OnboardingController {
    private final CustomerRepository customers;
    public OnboardingController(CustomerRepository customers){this.customers=customers;}
    public record Request(@NotBlank @Size(max=120) String name,@NotBlank @Email @Size(max=160) String email,@NotBlank @Pattern(regexp="ACC[A-F0-9]{24}") String accountNumber){}
    @PutMapping("/{id}") @Transactional public Customer provision(@PathVariable java.util.UUID id,@Valid @RequestBody Request request){
        var prior=customers.findById(id.toString());
        if(prior.isPresent()) {
            var c=prior.get();
            if(!c.getName().equals(request.name()) || !c.getEmail().equals(request.email()) || !c.getAccountNumber().equals(request.accountNumber()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Customer provisioning details differ");
            return c;
        }
        if(customers.existsByEmail(request.email()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");
        return customers.saveAndFlush(new Customer(id.toString(),request.name(),request.email(),request.accountNumber()));
    }
}
