package com.reportai.auth.port;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;

import java.util.Optional;

/**
 * Resolves a verification key for a JWT header. The application may implement this with a
 * cached JWKS client, database-backed key store, or an HSM. Implementations should cache by
 * (issuer, kid), refresh on an unknown kid, and evict keys according to their JWKS metadata.
 */
@FunctionalInterface
public interface JwtKeyResolver {

    Optional<JWK> resolve(String keyId, JWSAlgorithm algorithm);
}
