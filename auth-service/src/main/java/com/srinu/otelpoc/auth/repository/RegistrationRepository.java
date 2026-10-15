package com.srinu.otelpoc.auth.repository;
import com.srinu.otelpoc.auth.domain.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RegistrationRepository extends JpaRepository<Registration,String>{boolean existsByEmail(String email);}
