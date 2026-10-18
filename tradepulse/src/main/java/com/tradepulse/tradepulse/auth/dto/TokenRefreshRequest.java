package com.tradepulse.tradepulse.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(
        @NotBlank String refreshToken
) {}
