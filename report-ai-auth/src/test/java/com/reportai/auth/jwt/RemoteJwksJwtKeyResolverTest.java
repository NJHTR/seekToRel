package com.reportai.auth.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RemoteJwksJwtKeyResolverTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void cachesKeysAndRefreshesWhenKidIsUnknown() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("key-1").algorithm(JWSAlgorithm.RS256).generate();
        byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
        RemoteJwksJwtKeyResolver resolver = new RemoteJwksJwtKeyResolver(uri, Duration.ofSeconds(2),
                Duration.ofSeconds(2), 1_000_000, Duration.ofMinutes(5), Duration.ofMinutes(1), true);

        assertTrue(resolver.resolve("key-1", JWSAlgorithm.RS256).isPresent());
        assertTrue(resolver.resolve("key-1", JWSAlgorithm.RS256).isPresent());
        assertEquals(1, requests.get(), "cached key should not cause another HTTP call");
        assertTrue(resolver.resolve("unknown", JWSAlgorithm.RS256).isEmpty());
        assertEquals(2, requests.get(), "unknown kid should trigger one refresh");
    }

    @Test
    void rejectsInsecureEndpointUnlessExplicitlyAllowed() {
        assertThrows(IllegalArgumentException.class, () -> new RemoteJwksJwtKeyResolver(
                URI.create("http://identity.example.test/jwks"), Duration.ofSeconds(1), Duration.ofSeconds(1),
                1000, Duration.ofMinutes(5), Duration.ofMinutes(1), false));
    }

    @Test
    void rejectsAmbiguousOrInvalidInputs() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("key-1").algorithm(JWSAlgorithm.RS256).generate();
        byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
        RemoteJwksJwtKeyResolver resolver = new RemoteJwksJwtKeyResolver(uri, Duration.ofSeconds(1),
                Duration.ofSeconds(1), 1000, Duration.ofMinutes(5), Duration.ofMinutes(1), true);
        assertTrue(resolver.resolve("", JWSAlgorithm.RS256).isEmpty());
        assertTrue(resolver.resolve("key-1", null).isEmpty());
        assertTrue(resolver.resolve("key-1", JWSAlgorithm.ES256).isEmpty());
    }
}
