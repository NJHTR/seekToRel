package com.reportai.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.application.config.properties.ReportAiCatalogProperties;
import com.reportai.runtime.EnvironmentCatalog;
import com.reportai.runtime.EnvironmentCatalogException;
import com.reportai.runtime.EnvironmentResolver;
import com.reportai.runtime.EnvironmentManifest;
import com.reportai.runtime.PythonDependencyRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignedEnvironmentCatalogLoaderTest {
    private static final String IMAGE = "python@sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de";

    @Test
    void verifiesDetachedEd25519SignatureBeforeParsingCatalog() throws Exception {
        KeyPair pair = keyPair();
        Path directory = Files.createTempDirectory("reportai-catalog-");
        byte[] payload = payload();
        Path payloadPath = Files.write(directory.resolve("catalog.json"), payload);
        Path signaturePath = Files.write(directory.resolve("catalog.sig"), sign(pair, payload));
        ReportAiCatalogProperties properties = properties(payloadPath, signaturePath,
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));

        EnvironmentCatalog catalog = new SignedEnvironmentCatalogLoader(new ObjectMapper()).load(properties);
        assertEquals(IMAGE, catalog.profile("python").imageDigest());
        assertEquals("numpy", catalog.dependency("numpy", "1.26.4").name());
    }

    @Test
    void rejectsTamperedPayloadAndOversizedFile() throws Exception {
        KeyPair pair = keyPair();
        Path directory = Files.createTempDirectory("reportai-catalog-");
        Path payloadPath = Files.write(directory.resolve("catalog.json"), payload());
        Path signaturePath = Files.write(directory.resolve("catalog.sig"), sign(pair, Files.readAllBytes(payloadPath)));
        Files.writeString(payloadPath, "tampered", StandardCharsets.UTF_8);
        ReportAiCatalogProperties properties = properties(payloadPath, signaturePath,
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        assertThrows(EnvironmentCatalogException.class,
                () -> new SignedEnvironmentCatalogLoader(new ObjectMapper()).load(properties));
        properties.setMaxBytes(2);
        assertThrows(EnvironmentCatalogException.class,
                () -> new SignedEnvironmentCatalogLoader(new ObjectMapper()).load(properties));
    }

    @Test
    void loadsTransitiveRequiresEdgesFromSignedCatalog() throws Exception {
        KeyPair pair = keyPair();
        Path directory = Files.createTempDirectory("reportai-catalog-");
        byte[] payload = graphPayload();
        Path payloadPath = Files.write(directory.resolve("catalog.json"), payload);
        Path signaturePath = Files.write(directory.resolve("catalog.sig"), sign(pair, payload));
        EnvironmentCatalog catalog = new SignedEnvironmentCatalogLoader(new ObjectMapper()).load(properties(
                payloadPath, signaturePath, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));

        EnvironmentManifest manifest = new EnvironmentResolver(catalog).resolve("python", "main.py",
                new PythonDependencyRequest(java.util.List.of("numpy==1.26.4")));
        assertEquals(java.util.List.of("numpy", "packaging"),
                manifest.dependencies().stream().map(value -> value.name()).sorted().toList());
    }

    @Test
    void loadsTheCheckedInScientificCatalogAndVerifiesItsDetachedSignature() {
        Path root = Path.of(System.getProperty("user.dir"));
        while (!Files.exists(root.resolve("runtime-images/scientific/local-catalog"))
                && root.getParent() != null) {
            root = root.getParent();
        }
        Path catalog = root.resolve("runtime-images/scientific/local-catalog/scientific.catalog.json");
        Path signature = root.resolve("runtime-images/scientific/local-catalog/scientific.catalog.sig");
        Path publicKey = root.resolve("runtime-images/scientific/local-catalog/catalog-ed25519-public.pem");
        ReportAiCatalogProperties properties = new ReportAiCatalogProperties();
        properties.setPath(catalog.toString());
        properties.setSignaturePath(signature.toString());
        properties.setPublicKeyPath(publicKey.toString());

        EnvironmentCatalog loaded = new SignedEnvironmentCatalogLoader(new ObjectMapper()).load(properties);

        assertEquals("reportai/python-scientific@sha256:"
                        + "6aafbe96a24e380848caebd5c370abb62cafc845bb03b656bcb770d7407ac425",
                loaded.profile("python").imageDigest());
        assertEquals(23, loaded.dependencies().size());
        assertEquals(23, loaded.catalogDependencies().size());
    }

    private static ReportAiCatalogProperties properties(Path payload, Path signature, String publicKey) {
        ReportAiCatalogProperties properties = new ReportAiCatalogProperties();
        properties.setPath(payload.toString());
        properties.setSignaturePath(signature.toString());
        properties.setPublicKey(publicKey);
        return properties;
    }

    private static byte[] payload() {
        return ("{\"schemaVersion\":1,\"profiles\":[{\"language\":\"python\",\"imageDigest\":\""
                + IMAGE + "\",\"capabilityProfile\":\"scientific\"}],\"dependencies\":[{"
                + "\"name\":\"numpy\",\"version\":\"1.26.4\",\"sha256\":\""
                + "a".repeat(64) + "\"}]}" ).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] graphPayload() {
        return ("{\"schemaVersion\":1,\"profiles\":[{\"language\":\"python\",\"imageDigest\":\""
                + IMAGE + "\",\"capabilityProfile\":\"scientific\"}],\"dependencies\":[{"
                + "\"name\":\"numpy\",\"version\":\"1.26.4\",\"sha256\":\""
                + "a".repeat(64) + "\",\"requires\":[{\"name\":\"packaging\",\"version\":\"24.1\"}]},"
                + "{\"name\":\"packaging\",\"version\":\"24.1\",\"sha256\":\""
                + "b".repeat(64) + "\",\"requires\":[]}]}").getBytes(StandardCharsets.UTF_8);
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        return generator.generateKeyPair();
    }

    private static byte[] sign(KeyPair pair, byte[] payload) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(pair.getPrivate());
        signature.update(payload);
        return signature.sign();
    }
}
