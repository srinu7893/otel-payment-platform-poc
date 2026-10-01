package com.srinu.otelpoc.auth.repository;

import com.srinu.otelpoc.auth.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
}
