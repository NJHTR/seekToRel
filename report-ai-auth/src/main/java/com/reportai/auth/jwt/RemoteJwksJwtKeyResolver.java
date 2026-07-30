package com.reportai.auth.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.source.DefaultJWKSetCache;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.reportai.auth.port.JwtKeyResolver;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Production JWKS adapter backed by Nimbus' refresh-on-unknown-kid implementation.
 *
 * <p>The remote endpoint is constrained to HTTPS by default. The cache prevents a request to
 * the identity provider for every API call, while Nimbus refreshes once when a key id is not
 * found (supporting normal signing-key rotation). Failures are deliberately converted to an
 * empty result so authentication fails closed without leaking upstream details.</p>
 */
public final class RemoteJwksJwtKeyResolver implements JwtKeyResolver {
    private final RemoteJWKSet<com.nimbusds.jose.proc.SecurityContext> remoteJwkSet;

    public RemoteJwksJwtKeyResolver(URI jwksUri,
                                    Duration connectTimeout,
                                    Duration readTimeout,
                                    int responseSizeLimit,
                                    Duration cacheLifespan,
                                    Duration cacheRefresh,
                                    boolean allowInsecureHttp) {
        this.remoteJwkSet = create(jwksUri, connectTimeout, readTimeout, responseSizeLimit,
                cacheLifespan, cacheRefresh, allowInsecureHttp);
    }

    @Override
    public Optional<JWK> resolve(String keyId, JWSAlgorithm algorithm) {
        if (keyId == null || keyId.isBlank() || keyId.length() > 256 || algorithm == null) {
            return Optional.empty();
        }
        try {
            JWKMatcher matcher = new JWKMatcher.Builder()
                    .keyID(keyId)
                    .publicOnly(true)
                    .build();
            List<JWK> matches = remoteJwkSet.get(new JWKSelector(matcher), null);
            if (matches.size() != 1) {
                return Optional.empty();
            }
            JWK key = matches.get(0);
            // A remote JWKS must only supply asymmetric verification keys. Accepting a symmetric
            // secret from an HTTP endpoint would turn endpoint compromise into token forgery.
            if (!(key instanceof RSAKey || key instanceof ECKey)
                    || key.getAlgorithm() != null && !algorithm.equals(key.getAlgorithm())) {
                return Optional.empty();
            }
            return Optional.of(key);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static RemoteJWKSet<com.nimbusds.jose.proc.SecurityContext> create(
            URI jwksUri,
            Duration connectTimeout,
            Duration readTimeout,
            int responseSizeLimit,
            Duration cacheLifespan,
            Duration cacheRefresh,
            boolean allowInsecureHttp) {
        Objects.requireNonNull(jwksUri, "jwksUri");
        validateUri(jwksUri, allowInsecureHttp);
        int connectMs = durationMillis(connectTimeout, "connectTimeout", 1, 120_000);
        int readMs = durationMillis(readTimeout, "readTimeout", 1, 120_000);
        if (responseSizeLimit < 1 || responseSizeLimit > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("responseSizeLimit must be between 1 and 16777216");
        }
        long lifespanMs = durationMillisLong(cacheLifespan, "cacheLifespan", 1_000, 86_400_000);
        long refreshMs = durationMillisLong(cacheRefresh, "cacheRefresh", 1_000, lifespanMs);
        if (refreshMs > lifespanMs) {
            throw new IllegalArgumentException("cacheRefresh must not exceed cacheLifespan");
        }
        try {
            URL url = jwksUri.toURL();
            DefaultResourceRetriever retriever = new DefaultResourceRetriever(connectMs, readMs,
                    responseSizeLimit);
            DefaultJWKSetCache cache = new DefaultJWKSetCache(lifespanMs, refreshMs,
                    TimeUnit.MILLISECONDS);
            return new RemoteJWKSet<>(url, retriever, cache);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid JWKS URI", ex);
        }
    }

    private static void validateUri(URI uri, boolean allowInsecureHttp) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)
                && !(allowInsecureHttp && "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("JWKS URI must use HTTPS");
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getQuery() != null) {
            throw new IllegalArgumentException("JWKS URI contains an invalid authority or component");
        }
    }

    private static int durationMillis(Duration duration, String name, long min, long max) {
        long value = durationMillisLong(duration, name, min, max);
        return Math.toIntExact(value);
    }

    private static long durationMillisLong(Duration duration, String name, long min, long max) {
        Objects.requireNonNull(duration, name);
        long value;
        try {
            value = duration.toMillis();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(name + " is too large", ex);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " is outside the allowed range");
        }
        return value;
    }
}
