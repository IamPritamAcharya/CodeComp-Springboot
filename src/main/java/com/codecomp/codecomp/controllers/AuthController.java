package com.codecomp.codecomp.controllers;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
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

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Value("${frontend.url}")
    private String frontendUrl;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam Long userId) {

        String token = jwtUtil.generateToken(userId);

        return Map.of(
                "token", token);
    }

    @GetMapping("/oauth-success")
    public void oauthSuccess(Authentication authentication, HttpServletResponse response) throws IOException {

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

        String redirectUrl = frontendUrl + "/oauth-success" +
                "?token=" + token +
                "&userId=" + user.getId() +
                "&email=" + user.getEmail();

        response.sendRedirect(redirectUrl);
    }
}