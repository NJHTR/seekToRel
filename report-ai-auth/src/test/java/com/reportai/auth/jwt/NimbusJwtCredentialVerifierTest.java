package com.reportai.auth.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.reportai.auth.exception.AuthenticationRequiredException;
import com.reportai.auth.model.Permission;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NimbusJwtCredentialVerifierTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final java.security.KeyPair RSA_KEY_PAIR = generateKeyPair();

    @Test
    void verifiesSignatureAndTrustedClaims() throws Exception {
        RSAKey signingKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) RSA_KEY_PAIR.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) RSA_KEY_PAIR.getPrivate())
                .keyID("kid-1").build();
        String token = token(signingKey, NOW.plusSeconds(300), NOW.minusSeconds(1), "issuer-a");
        NimbusJwtCredentialVerifier verifier = new NimbusJwtCredentialVerifier(
                (kid, algorithm) -> "kid-1".equals(kid) ? java.util.Optional.of(signingKey.toPublicJWK()) : java.util.Optional.empty(),
                JwtVerificationPolicy.rs256("issuer-a", "report-ai"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        TenantPrincipal principal = verifier.verify(token);

        assertEquals("user-1", principal.subjectId());
        assertEquals(new TenantId("tenant-1"), principal.tenantId());
        assertEquals(Set.of(Permission.TASK_CREATE, Permission.DOCUMENT_READ), principal.permissions());
    }

    @Test
    void rejectsWrongIssuerAndExpiredCredentialsWithoutLeakingToken() throws Exception {
        RSAKey signingKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) RSA_KEY_PAIR.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) RSA_KEY_PAIR.getPrivate())
                .keyID("kid-1").build();
        String token = token(signingKey, NOW.plusSeconds(300), NOW.minusSeconds(1), "wrong-issuer");
        NimbusJwtCredentialVerifier verifier = new NimbusJwtCredentialVerifier(
                (kid, algorithm) -> java.util.Optional.of(signingKey.toPublicJWK()),
                JwtVerificationPolicy.rs256("issuer-a", "report-ai"), Clock.fixed(NOW, ZoneOffset.UTC));

        AuthenticationRequiredException error = assertThrows(AuthenticationRequiredException.class,
                () -> verifier.verify(token));
        assertEquals("invalid bearer credential", error.getMessage());
    }

    @Test
    void rejectsMissingTenantAndJti() throws Exception {
        RSAKey signingKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) RSA_KEY_PAIR.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) RSA_KEY_PAIR.getPrivate())
                .keyID("kid-1").build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("user-1").issuer("issuer-a")
                .audience("report-ai").expirationTime(Date.from(NOW.plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("kid-1").build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        NimbusJwtCredentialVerifier verifier = new NimbusJwtCredentialVerifier(
                (kid, algorithm) -> java.util.Optional.of(signingKey.toPublicJWK()),
                JwtVerificationPolicy.rs256("issuer-a", "report-ai"), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(AuthenticationRequiredException.class, () -> verifier.verify(jwt.serialize()));
    }

    private static String token(RSAKey key, Instant exp, Instant nbf, String issuer) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("user-1").jwtID("jti-1")
                .issuer(issuer).audience("report-ai").expirationTime(Date.from(exp))
                .notBeforeTime(Date.from(nbf)).claim("tenant_id", "tenant-1")
                .claim("permissions", List.of("TASK_CREATE", "DOCUMENT_READ")).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("kid-1").build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static java.security.KeyPair generateKeyPair() {
        try {
            java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
