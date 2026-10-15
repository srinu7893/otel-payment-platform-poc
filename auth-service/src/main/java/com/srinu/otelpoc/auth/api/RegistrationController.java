package com.srinu.otelpoc.auth.api;
import com.srinu.otelpoc.auth.service.RegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth")
public class RegistrationController {
    private final RegistrationService registration;
    public RegistrationController(RegistrationService registration){this.registration=registration;}
    public record Request(@NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._-]{2,39}") String username,
        @NotBlank @Size(min=12,max=72) String password,@NotBlank @Size(max=120) String name,@NotBlank @Email @Size(max=160) String email){
        @Override public String toString(){return "RegistrationRequest[redacted]";}
    }
    @PostMapping("/register") public RegistrationService.Result register(@Valid @RequestBody Request request){return registration.register(request.username(),request.password(),request.name(),request.email());}
}
