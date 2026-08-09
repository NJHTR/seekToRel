package com.reportai.auth.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.reportai.auth.model.Permission;
import com.reportai.auth.model.UserAccount;
import com.reportai.auth.port.JwtCredentialVerifier;
import com.reportai.auth.port.JwtKeyResolver;
import com.reportai.common.tenant.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Issues and verifies short-lived local access tokens and opaque refresh tokens. */
public final class LocalJwtTokenService implements JwtCredentialVerifier {
    private static final JWSAlgorithm ALGORITHM = JWSAlgorithm.HS256;
    private final LocalAuthProperties properties;
    private final Clock clock;
    private final byte[] signingSecret;
    private final JwtCredentialVerifier verifier;

    public LocalJwtTokenService(LocalAuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LocalJwtTokenService(LocalAuthProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        properties.validate();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.signingSecret = properties.getSigningSecret().getBytes(StandardCharsets.UTF_8);
        JWK verificationKey = new OctetSequenceKey.Builder(signingSecret)
                .keyID(properties.getKeyId())
                .algorithm(ALGORITHM)
                .build();
        JwtKeyResolver keyResolver = (keyId, algorithm) ->
                properties.getKeyId().equals(keyId) && ALGORITHM.equals(algorithm)
                        ? java.util.Optional.of(verificationKey) : java.util.Optional.empty();
        this.verifier = new NimbusJwtCredentialVerifier(keyResolver,
                new JwtVerificationPolicy(properties.getIssuer(), properties.getAudience(),
                        Set.of(ALGORITHM), java.time.Duration.ofSeconds(30), JwtClaimNames.defaults()), clock);
    }

    public IssuedTokenPair issue(UserAccount account, Collection<Permission> permissions) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(permissions, "permissions");
        Instant now = Instant.now(clock);
        Instant accessExpiresAt = now.plus(properties.getAccessTokenTtl());
        String accessToken = signAccessToken(account, permissions, now, accessExpiresAt);
        Instant refreshExpiresAt = now.plus(properties.getRefreshTokenTtl());
        String rawRefreshToken = randomToken();
        return new IssuedTokenPair(accessToken, accessExpiresAt,
                new RefreshTokenMaterial(UUID.randomUUID().toString(), rawRefreshToken,
                        sha256(rawRefreshToken), refreshExpiresAt));
    }

    @Override
    public com.reportai.auth.model.TenantPrincipal verify(String bearerToken) {
        return verifier.verify(bearerToken);
    }

    public long accessTokenExpiresInSeconds(Instant expiresAt) {
        return Math.max(0, expiresAt.getEpochSecond() - Instant.now(clock).getEpochSecond());
    }

    public long refreshTokenExpiresInSeconds(Instant expiresAt) {
        return Math.max(0, expiresAt.getEpochSecond() - Instant.now(clock).getEpochSecond());
    }

    public static String hashRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 512) {
            throw new IllegalArgumentException("refresh token is invalid");
        }
        return sha256(rawToken.trim());
    }

    private String signAccessToken(UserAccount account, Collection<Permission> permissions,
                                   Instant issuedAt, Instant expiresAt) {
        List<String> permissionNames = permissions.stream().filter(Objects::nonNull)
                .map(Enum::name).sorted().toList();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.getIssuer())
                .audience(properties.getAudience())
                .subject(account.id())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(issuedAt))
                .notBeforeTime(Date.from(issuedAt.minusSeconds(1)))
                .expirationTime(Date.from(expiresAt))
                .claim("tenant_id", account.tenantId().value())
                .claim("permissions", permissionNames)
                .build();
        try {
            SignedJWT signed = new SignedJWT(
                    new com.nimbusds.jose.JWSHeader.Builder(ALGORITHM)
                            .type(com.nimbusds.jose.JOSEObjectType.JWT)
                            .keyID(properties.getKeyId())
                            .build(), claims);
            signed.sign(new MACSigner(signingSecret));
            return signed.serialize();
        } catch (Exception failure) {
            throw new IllegalStateException("local access token could not be issued", failure);
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[48];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }
}
