package com.reportai.auth.jwt;

import java.time.Instant;

/** Raw refresh token is returned once; only its hash is persisted. */
public record RefreshTokenMaterial(
        String id,
        String rawToken,
        String tokenHash,
        Instant expiresAt) {
}
