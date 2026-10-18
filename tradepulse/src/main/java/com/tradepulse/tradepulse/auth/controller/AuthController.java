package com.tradepulse.tradepulse.auth.controller;

import com.tradepulse.tradepulse.auth.dto.*;
import com.tradepulse.tradepulse.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, token refresh and logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public AuthResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout and invalidate refresh tokens")
    public void logout(@AuthenticationPrincipal UserDetails userDetails) {
        // extract userId from principal — look it up in service if needed
        // For simplicity the AuthService looks up by username
        // We call a method that accepts email
        authService.logoutByEmail(userDetails.getUsername());
    }
}
