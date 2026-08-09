package com.reportai.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.application.config.properties.ReportAiCatalogProperties;
import com.reportai.runtime.DependencySpec;
import com.reportai.runtime.CatalogDependency;
import com.reportai.runtime.DependencyCoordinate;
import com.reportai.runtime.EnvironmentCatalog;
import com.reportai.runtime.EnvironmentCatalogException;
import com.reportai.runtime.RuntimeImageProfile;
import com.reportai.runtime.SignedCatalogVerifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Loads and verifies a detached Ed25519-signed catalog without network access. */
public final class SignedEnvironmentCatalogLoader {
    private static final long MAX_SIGNATURE_BYTES = 16 * 1024;
    private final ObjectMapper objectMapper;

    public SignedEnvironmentCatalogLoader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public EnvironmentCatalog load(ReportAiCatalogProperties properties) {
        if (properties == null || properties.getPath() == null || properties.getPath().isBlank()) {
            throw failure("CATALOG_NOT_CONFIGURED", "catalog path is not configured");
        }
        if (properties.getSignaturePath() == null || properties.getSignaturePath().isBlank()) {
            throw failure("CATALOG_SIGNATURE_MISSING", "catalog signature path is not configured");
        }
        if (properties.getMaxBytes() <= 0 || properties.getMaxBytes() > SignedCatalogVerifier.MAX_PAYLOAD_BYTES) {
            throw failure("CATALOG_LIMIT_INVALID", "catalog maxBytes is outside the permitted range");
        }
        Path payload = safeFile(properties.getPath(), properties.getMaxBytes(), "catalog payload");
        Path signature = safeFile(properties.getSignaturePath(), MAX_SIGNATURE_BYTES, "catalog signature");
        byte[] publicKey = readPublicKey(properties);
        try {
            byte[] bytes = Files.readAllBytes(payload);
            byte[] signatureBytes = decodeSignature(Files.readAllBytes(signature));
            new SignedCatalogVerifier().verify(new String(bytes, StandardCharsets.UTF_8),
                    Base64.getEncoder().encodeToString(signatureBytes),
                    Base64.getEncoder().encodeToString(publicKey));
            return parse(bytes);
        } catch (EnvironmentCatalogException failure) {
            throw failure;
        } catch (Exception failure) {
            throw failure("CATALOG_INVALID", "signed catalog could not be loaded");
        }
    }

    private byte[] readPublicKey(ReportAiCatalogProperties properties) {
        try {
            byte[] encoded;
            if (properties.getPublicKeyPath() != null && !properties.getPublicKeyPath().isBlank()) {
                Path path = safeFile(properties.getPublicKeyPath(), MAX_SIGNATURE_BYTES, "catalog public key");
                encoded = Files.readAllBytes(path);
            } else if (properties.getPublicKey() != null && !properties.getPublicKey().isBlank()) {
                encoded = properties.getPublicKey().getBytes(StandardCharsets.UTF_8);
            } else {
                throw failure("CATALOG_PUBLIC_KEY_MISSING", "catalog public key is not configured");
            }
            String text = new String(encoded, StandardCharsets.UTF_8).trim()
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            return Base64.getDecoder().decode(text);
        } catch (EnvironmentCatalogException failure) {
            throw failure;
        } catch (Exception failure) {
            throw failure("CATALOG_PUBLIC_KEY_INVALID", "catalog public key is invalid");
        }
    }

    private static byte[] decodeSignature(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8).trim().replaceAll("\\s+", "");
        if (text.matches("[A-Za-z0-9+/=_\\-]+")) {
            try { return Base64.getDecoder().decode(text); } catch (IllegalArgumentException ignored) { }
        }
        return bytes;
    }

    private EnvironmentCatalog parse(byte[] bytes) throws Exception {
        JsonNode root = objectMapper.readTree(bytes);
        if (root == null || !root.isObject() || root.path("schemaVersion").asInt(0) != 1) {
            throw failure("CATALOG_SCHEMA_INVALID", "catalog schemaVersion must be 1");
        }
        List<RuntimeImageProfile> profiles = new ArrayList<>();
        JsonNode profileNodes = root.get("profiles");
        if (profileNodes == null || !profileNodes.isArray()) throw failure("CATALOG_SCHEMA_INVALID", "profiles must be an array");
        for (JsonNode node : profileNodes) {
            profiles.add(new RuntimeImageProfile(required(node, "language"), required(node, "imageDigest"),
                    node.path("capabilityProfile").asText("default")));
        }
        List<CatalogDependency> dependencies = new ArrayList<>();
        JsonNode dependencyNodes = root.get("dependencies");
        if (dependencyNodes != null) {
            if (!dependencyNodes.isArray()) throw failure("CATALOG_SCHEMA_INVALID", "dependencies must be an array");
            for (JsonNode node : dependencyNodes) {
                DependencySpec dependency = new DependencySpec(required(node, "name"), required(node, "version"),
                        required(node, "sha256"));
                List<DependencyCoordinate> requires = new ArrayList<>();
                JsonNode requiresNode = node.get("requires");
                if (requiresNode != null) {
                    if (!requiresNode.isArray()) {
                        throw failure("CATALOG_SCHEMA_INVALID", "requires must be an array");
                    }
                    for (JsonNode requiredNode : requiresNode) {
                        requires.add(new DependencyCoordinate(required(requiredNode, "name"),
                                required(requiredNode, "version")));
                    }
                }
                dependencies.add(new CatalogDependency(dependency, requires));
            }
        }
        return EnvironmentCatalog.withDependencyGraph(profiles, dependencies);
    }

    private static String required(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field) || !node.get(field).isTextual()
                || node.get(field).textValue().isBlank()) {
            throw failure("CATALOG_SCHEMA_INVALID", field + " is required");
        }
        return node.get(field).textValue();
    }

    private static Path safeFile(String raw, long maxBytes, String label) {
        try {
            Path path = Path.of(raw).toAbsolutePath().normalize();
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw failure("CATALOG_FILE_INVALID", label + " must be a regular non-symlink file");
            }
            long size = Files.size(path);
            if (size <= 0 || size > maxBytes) throw failure("CATALOG_FILE_TOO_LARGE", label + " exceeds size limit");
            return path;
        } catch (EnvironmentCatalogException failure) {
            throw failure;
        } catch (Exception failure) {
            throw failure("CATALOG_FILE_INVALID", label + " cannot be inspected");
        }
    }

    private static EnvironmentCatalogException failure(String code, String message) {
        EnvironmentCatalogException.Code category = "CATALOG_SIGNATURE_INVALID".equals(code)
                ? EnvironmentCatalogException.Code.CATALOG_SIGNATURE_INVALID
                : EnvironmentCatalogException.Code.CATALOG_INVALID;
        return new EnvironmentCatalogException(category,
                code + ": " + message);
    }
}
