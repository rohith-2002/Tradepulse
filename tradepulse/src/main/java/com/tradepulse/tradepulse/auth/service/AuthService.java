package com.tradepulse.tradepulse.auth.service;

import com.tradepulse.tradepulse.auth.dto.*;
import com.tradepulse.tradepulse.auth.entity.RefreshToken;
import com.tradepulse.tradepulse.auth.entity.User;
import com.tradepulse.tradepulse.auth.repository.RefreshTokenRepository;
import com.tradepulse.tradepulse.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + request.email());
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role("ROLE_USER")
                .build();
        user = userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(TokenRefreshRequest request) {
        RefreshToken token = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new BadCredentialsException("Refresh token expired");
        }
        User user = token.getUser();
        refreshTokenRepository.delete(token);
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Transactional
    public void logoutByEmail(String email) {
        userRepository.findByEmail(email).ifPresent(user -> refreshTokenRepository.deleteByUserId(user.getId()));
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user);
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpiration / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);
        return AuthResponse.of(accessToken, refreshToken.getToken(), user.getId(), user.getEmail(), user.getRole());
    }
}
