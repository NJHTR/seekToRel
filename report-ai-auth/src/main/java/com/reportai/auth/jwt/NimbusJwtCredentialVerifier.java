package com.reportai.auth.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.reportai.auth.exception.AuthenticationRequiredException;
import com.reportai.auth.model.Permission;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.auth.port.JwtCredentialVerifier;
import com.reportai.auth.port.JwtKeyResolver;
import com.reportai.common.tenant.TenantId;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nimbus JOSE implementation of the JWT verification port.
 *
 * <p>This adapter deliberately requires a {@code kid}, an allow-listed signing algorithm, an
 * exact issuer/audience match, and the identity claims used by ReportAI. Key retrieval is
 * delegated to {@link JwtKeyResolver}; that port is where JWKS caching, rotation and refresh on
 * an unknown key id belong.</p>
 */
public final class NimbusJwtCredentialVerifier implements JwtCredentialVerifier {
    private static final int MAX_TOKEN_LENGTH = 16_384;
    private static final int MAX_CLAIM_LENGTH = 512;

    private final JwtKeyResolver keyResolver;
    private final JwtVerificationPolicy policy;
    private final Clock clock;

    public NimbusJwtCredentialVerifier(JwtKeyResolver keyResolver, JwtVerificationPolicy policy) {
        this(keyResolver, policy, Clock.systemUTC());
    }

    public NimbusJwtCredentialVerifier(JwtKeyResolver keyResolver,
                                       JwtVerificationPolicy policy,
                                       Clock clock) {
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public TenantPrincipal verify(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank() || bearerToken.length() > MAX_TOKEN_LENGTH) {
            throw invalidCredential();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(bearerToken);
            JWSAlgorithm algorithm = jwt.getHeader().getAlgorithm();
            if (!policy.allowedAlgorithms().contains(algorithm)) {
                throw invalidCredential();
            }
            if (jwt.getHeader().getType() != null
                    && !"JWT".equalsIgnoreCase(jwt.getHeader().getType().toString())) {
                throw invalidCredential();
            }
            if (jwt.getHeader().getCriticalParams() != null
                    && !jwt.getHeader().getCriticalParams().isEmpty()) {
                throw invalidCredential();
            }
            String keyId = jwt.getHeader().getKeyID();
            if (keyId == null || keyId.isBlank() || keyId.length() > 256) {
                throw invalidCredential();
            }
            JWK key = keyResolver.resolve(keyId, algorithm).orElseThrow(NimbusJwtCredentialVerifier::invalidCredential);
            if (key.getAlgorithm() != null && !algorithm.equals(key.getAlgorithm())) {
                throw invalidCredential();
            }
            JWSVerifier verifier = verifierFor(key, algorithm);
            if (!jwt.verify(verifier)) {
                throw invalidCredential();
            }
            return mapClaims(jwt.getJWTClaimsSet());
        } catch (AuthenticationRequiredException ex) {
            throw ex;
        } catch (Exception ex) {
            // Never expose parser/key details and never log the bearer credential here.
            throw invalidCredential();
        }
    }

    private TenantPrincipal mapClaims(JWTClaimsSet claims) throws ParseException {
        String issuer = claims.getIssuer();
        if (!policy.issuer().equals(issuer)) {
            throw invalidCredential();
        }
        List<String> audience = claims.getAudience();
        if (audience == null || audience.stream().noneMatch(policy.audience()::equals)) {
            throw invalidCredential();
        }

        Instant now = Instant.now(clock);
        Instant expiresAt = instantClaim(claims.getExpirationTime());
        // exp is mandatory and is never silently defaulted. A small skew is accepted for nbf,
        // but the principal is not kept alive beyond its actual expiration timestamp.
        if (!expiresAt.isAfter(now)) {
            throw invalidCredential();
        }
        if (claims.getNotBeforeTime() != null
                && claims.getNotBeforeTime().toInstant().isAfter(now.plus(policy.clockSkew()))) {
            throw invalidCredential();
        }

        String subject = bounded(claims.getSubject());
        String tokenId = bounded(claims.getJWTID());
        String tenantValue = bounded(claims.getStringClaim(policy.claimNames().tenant()));
        if (subject == null || tokenId == null || tenantValue == null) {
            throw invalidCredential();
        }
        TenantId tenantId;
        try {
            tenantId = TenantId.of(tenantValue);
        } catch (RuntimeException ex) {
            throw invalidCredential();
        }
        return TenantPrincipal.authenticated(subject, tenantId,
                permissions(claims.getClaim(policy.claimNames().permissions())), expiresAt, tokenId);
    }

    private Set<Permission> permissions(Object raw) {
        if (raw == null) {
            return Set.of();
        }
        Collection<?> values;
        if (raw instanceof String text) {
            values = List.of(text.split("[,\\s]+"));
        } else if (raw instanceof Collection<?> collection) {
            values = collection;
        } else {
            throw invalidCredential();
        }
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (Object value : values) {
            if (!(value instanceof String permissionText) || permissionText.length() > 128) {
                throw invalidCredential();
            }
            String normalized = permissionText.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                result.add(Permission.valueOf(normalized.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                // Unknown permissions are not granted. This allows providers to add unrelated
                // scopes without turning a typo into a privilege escalation.
            }
        }
        return Set.copyOf(result);
    }

    private JWSVerifier verifierFor(JWK key, JWSAlgorithm algorithm) throws Exception {
        if (key instanceof RSAKey rsaKey) {
            if (!isRsaAlgorithm(algorithm)) {
                throw invalidCredential();
            }
            return new RSASSAVerifier(rsaKey.toRSAPublicKey());
        }
        if (key instanceof ECKey ecKey) {
            if (!algorithm.getName().startsWith("ES")) {
                throw invalidCredential();
            }
            return new ECDSAVerifier(ecKey.toECPublicKey());
        }
        if (key instanceof OctetSequenceKey octetKey) {
            if (!algorithm.getName().startsWith("HS")) {
                throw invalidCredential();
            }
            return new MACVerifier(octetKey.toByteArray());
        }
        throw invalidCredential();
    }

    private static boolean isRsaAlgorithm(JWSAlgorithm algorithm) {
        String name = algorithm.getName();
        return name.startsWith("RS") || name.startsWith("PS");
    }

    private static Instant instantClaim(java.util.Date value) {
        return value == null ? null : value.toInstant();
    }

    private static String bounded(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() || normalized.length() > MAX_CLAIM_LENGTH ? null : normalized;
    }

    private static AuthenticationRequiredException invalidCredential() {
        return new AuthenticationRequiredException("invalid bearer credential");
    }
}
