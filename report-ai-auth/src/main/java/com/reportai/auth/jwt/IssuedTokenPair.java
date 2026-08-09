package com.reportai.auth.jwt;

import java.time.Instant;

/** Newly issued credentials plus the persistence material for the refresh token. */
public record IssuedTokenPair(
        String accessToken,
        Instant accessTokenExpiresAt,
        RefreshTokenMaterial refreshToken) {
}
