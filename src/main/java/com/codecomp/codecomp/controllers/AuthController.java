package com.codecomp.codecomp.controllers;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codecomp.codecomp.models.User;
import com.codecomp.codecomp.repository.UserRepository;
import com.codecomp.codecomp.security.JwtUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam Long userId) {

        String token = jwtUtil.generateToken(userId);

        return Map.of(
                "token", token);
    }

    @GetMapping("/oauth-success")
    public Map<String, Object> oauthSuccess(Authentication authentication) {

        if (authentication == null) {
            throw new RuntimeException("User not authenticated via OAuth2");
        }

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");

        User user = userRepository.findByEmail(email)
                .map(existing -> {
                    existing.setName(name);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setName(name);
                    newUser.setProvider("GOOGLE");
                    return userRepository.save(newUser);
                });

        String token = jwtUtil.generateToken(user.getId());

        return Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "token", token);
    }
}