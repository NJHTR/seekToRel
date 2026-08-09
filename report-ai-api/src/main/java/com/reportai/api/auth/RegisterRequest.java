package com.reportai.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public account-registration contract. */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(min = 1, max = 128) String displayName,
        @NotBlank @Size(min = 1, max = 128) String tenantName) {
}
