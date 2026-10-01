package com.srinu.otelpoc.auth.config;

import com.srinu.otelpoc.auth.domain.AppUser;
import com.srinu.otelpoc.auth.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
public class DemoUserConfig {
    @Bean
    CommandLineRunner seedDemoUsers(AppUserRepository users, PasswordEncoder passwordEncoder) {
        return args -> {
            if (!users.existsById("demo")) {
                users.save(new AppUser("demo", passwordEncoder.encode("demo123"), "demo-customer", Set.of("CUSTOMER")));
            }
            if (!users.existsById("receiver")) {
                users.save(new AppUser("receiver", passwordEncoder.encode("receiver123"), "receiver-customer", Set.of("CUSTOMER")));
            }
            if (!users.existsById("support")) {
                users.save(new AppUser("support", passwordEncoder.encode("support123"), "support-user", Set.of("SUPPORT")));
            }
            if (!users.existsById("admin")) {
                users.save(new AppUser("admin", passwordEncoder.encode("admin123"), "admin-user", Set.of("ADMIN")));
            }
        };
    }
}
