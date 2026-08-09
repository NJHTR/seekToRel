package com.reportai.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Refresh-token rotation contract. */
public record RefreshTokenRequest(
        @NotBlank @Size(max = 512) String refreshToken) {
}
