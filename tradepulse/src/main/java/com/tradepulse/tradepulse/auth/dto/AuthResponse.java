package com.tradepulse.tradepulse.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long userId,
        String email,
        String role
) {
    public static AuthResponse of(String access, String refresh, Long userId, String email, String role) {
        return new AuthResponse(access, refresh, "Bearer", userId, email, role);
    }
}
