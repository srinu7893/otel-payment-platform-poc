package com.srinu.otelpoc.auth.api;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import org.slf4j.*; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/auth") public class AuthController {
 private static final Logger log=LoggerFactory.getLogger(AuthController.class);
 @PostMapping("/login") public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest r){
  if(!"demo".equals(r.username()) || !"demo123".equals(r.password())){log.warn("event=LOGIN_FAILED username={}",r.username()); return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();}
  var token="demo-token-"+UUID.randomUUID(); log.info("event=LOGIN_SUCCEEDED username={}",r.username()); return ResponseEntity.ok(new LoginResponse(token,"demo-customer")); }
 public record LoginRequest(@NotBlank String username,@NotBlank String password){} public record LoginResponse(String accessToken,String customerId){}
}
