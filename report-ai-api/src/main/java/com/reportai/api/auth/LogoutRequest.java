package com.reportai.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Refresh-token revocation contract. */
public record LogoutRequest(
        @NotBlank @Size(max = 512) String refreshToken) {
}
