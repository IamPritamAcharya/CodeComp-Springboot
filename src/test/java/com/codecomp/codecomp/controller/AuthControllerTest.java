package com.codecomp.codecomp.controller;

import com.codecomp.codecomp.controllers.AuthController;
import com.codecomp.codecomp.models.User;
import com.codecomp.codecomp.repository.UserRepository;
import com.codecomp.codecomp.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthController authController;

    @Test
    void login_shouldReturnToken() {
        when(jwtUtil.generateToken(10L)).thenReturn("jwt-token");

        Map<String, Object> response = authController.login(10L);

        org.junit.jupiter.api.Assertions.assertEquals("jwt-token", response.get("token"));
    }

    @Test
    void oauthSuccess_shouldCreateUserAndRedirect() throws Exception {
        ReflectionTestUtils.setField(authController, "frontendUrl", "http://localhost:3000");

        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        OAuth2User oauthUser = org.mockito.Mockito.mock(OAuth2User.class);
        HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);

        when(authentication.getPrincipal()).thenReturn(oauthUser);
        when(oauthUser.getAttribute("email")).thenReturn("alice@example.com");
        when(oauthUser.getAttribute("name")).thenReturn("Alice");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });

        when(jwtUtil.generateToken(42L)).thenReturn("jwt-token");

        authController.oauthSuccess(authentication, response);

        verify(response).sendRedirect("http://localhost:3000/oauth-success?token=jwt-token&userId=42&email=alice@example.com");
    }
}