package com.reportai.api.auth;

/** Access/refresh credentials returned after registration, login or rotation. */
public record AuthTokenResponse(
        String accessToken,
        long accessTokenExpiresInSeconds,
        String refreshToken,
        long refreshTokenExpiresInSeconds,
        String userId,
        String tenantId,
        String email,
        String displayName) {
}
