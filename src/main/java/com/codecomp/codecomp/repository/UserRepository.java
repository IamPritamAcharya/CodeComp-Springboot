package com.codecomp.codecomp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codecomp.codecomp.models.User;

public interface UserRepository extends JpaRepository<User, Long> {

    // SELECT * FROM users WHERE email = ?
    Optional<User> findByEmail(String email);
}
