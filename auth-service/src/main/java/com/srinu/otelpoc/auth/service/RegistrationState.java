package com.srinu.otelpoc.auth.service;
import com.srinu.otelpoc.auth.domain.*;
import com.srinu.otelpoc.auth.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;
import java.util.Set;

@Service public class RegistrationState {
    private final AppUserRepository users;
    private final RegistrationRepository registrations;
    private final PasswordEncoder passwords;
    public RegistrationState(AppUserRepository users,RegistrationRepository registrations,PasswordEncoder passwords){this.users=users;this.registrations=registrations;this.passwords=passwords;}
    @Transactional public Registration reserve(String username,String password,String name,String email){
        var existing=users.findById(username);
        if(existing.isPresent()){
            var user=existing.get();var registration=registrations.findById(username);
            if(registration.isEmpty() || !passwords.matches(password,user.getPasswordHash()))throw new RegistrationService.RegistrationConflict();
            var r=registration.get();
            if(!r.getDisplayName().equals(name) || !r.getEmail().equals(email))throw new RegistrationService.RegistrationConflict();
            // A lost successful HTTP response is safely replayed only after verifying the password.
            return r;
        }
        if(registrations.existsByEmail(email))throw new RegistrationService.RegistrationConflict();
        String customer=UUID.randomUUID().toString();
        var user=new AppUser(username,passwords.encode(password),customer,Set.of("CUSTOMER"));user.disable();users.saveAndFlush(user);
        String account="ACC"+UUID.randomUUID().toString().replace("-","").substring(0,24).toUpperCase(java.util.Locale.ROOT);
        return registrations.saveAndFlush(new Registration(username,account,name,email));
    }
    @Transactional public void activate(String username){
        var user=users.findById(username).orElseThrow();var registration=registrations.findById(username).orElseThrow();
        registration.complete();user.enable();
    }
    @Transactional(readOnly=true) public String customerId(String username){return users.findById(username).orElseThrow().getCustomerId();}
    @Transactional public void discardUnprovisioned(String username){
        var registration=registrations.findById(username).orElseThrow();
        var user=users.findById(username).orElseThrow();
        if("PENDING".equals(registration.getState()) && !user.isEnabled()){
            registrations.delete(registration);registrations.flush();users.delete(user);
        }
    }
}
