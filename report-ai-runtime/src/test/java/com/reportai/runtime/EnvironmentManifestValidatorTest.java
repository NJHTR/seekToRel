package com.reportai.runtime;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentManifestValidatorTest {
    private static final String IMAGE = "python@sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de";
    private static final String HASH = "0".repeat(64);

    @Test
    void validatesLockedManifestAndContentHash() throws Exception {
        Path workspace = Files.createTempDirectory("reportai-preflight-");
        Path inputRoot = Files.createDirectories(workspace.resolve("input"));
        Path input = Files.writeString(inputRoot.resolve("data.txt"), "hello", StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(input)));
        EnvironmentManifest manifest = new EnvironmentManifest(1, "python", "main.py",
                List.of(new DependencySpec("numpy", "1.26.4", HASH)),
                List.of(new InputRequirement("data.txt", InputRequirement.Kind.FILE, 1024, hash)), IMAGE, "scientific");

        PreflightResult result = new EnvironmentManifestValidator().validate(manifest, workspace);
        assertTrue(result.accepted());
        assertTrue(result.violations().isEmpty());
        assertEquals(64, manifest.manifestDigest().length());
    }

    @Test
    void reportsMissingLargeAndMismatchedInputs() throws Exception {
        Path workspace = Files.createTempDirectory("reportai-preflight-");
        Path inputRoot = Files.createDirectories(workspace.resolve("input"));
        Files.writeString(inputRoot.resolve("large.bin"), "0123456789");
        Files.writeString(inputRoot.resolve("mismatch.bin"), "content");
        EnvironmentManifest manifest = new EnvironmentManifest(1, "python", "main.py", List.of(), List.of(
                new InputRequirement("missing.csv", 100),
                new InputRequirement("large.bin", 2),
                new InputRequirement("mismatch.bin", InputRequirement.Kind.FILE, 100, HASH)), IMAGE, "default");

        PreflightResult result = new EnvironmentManifestValidator().validate(manifest, workspace);
        assertFalse(result.accepted());
        assertEquals(List.of("INPUT_MISSING", "INPUT_TOO_LARGE", "INPUT_HASH_MISMATCH"),
                result.violations().stream().map(PreflightResult.Violation::code).toList());
    }

    @Test
    void rejectsUnsafePathAndUnpinnedDependency() {
        assertThrows(IllegalArgumentException.class, () -> new InputRequirement("../secret", 100));
        assertThrows(IllegalArgumentException.class, () -> new DependencySpec("numpy", "^1.2.0"));
        EnvironmentManifest manifest = new EnvironmentManifest(1, "python", "main.py",
                List.of(new DependencySpec("numpy", "1.26.4")), List.of(), IMAGE, "default");
        PreflightResult result = new EnvironmentManifestValidator().validate(manifest, Path.of("."));
        assertFalse(result.accepted());
        assertEquals("DEPENDENCY_HASH_MISSING", result.violations().get(0).code());
    }

    @Test
    void adaptsLegacyPythonDependencies() {
        PythonDependencyRequest legacy = new PythonDependencyRequest(List.of("numpy==1.26.4"));
        EnvironmentManifest manifest = EnvironmentManifest.fromLegacy("python", "main.py", legacy, IMAGE);
        assertEquals(List.of("numpy==1.26.4"), PythonDependencyRequest.fromManifest(manifest).packages());
        assertEquals("numpy", manifest.dependencies().get(0).name());
    }

    @Test
    void rejectsUnsupportedLanguageAndUnsafeCapabilityProfile() {
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentManifest(1, "python", "main.py",
                List.of(), List.of(), IMAGE, "scientific profile"));
        EnvironmentManifest manifest = new EnvironmentManifest(1, "ruby", "main.rb",
                List.of(), List.of(), IMAGE, "default");
        PreflightResult result = new EnvironmentManifestValidator().validate(manifest, Path.of("."));
        assertTrue(result.violations().stream().anyMatch(v -> v.code().equals("LANGUAGE_UNSUPPORTED")));
    }

    @Test
    void rejectsScientificTorchExecutionWithoutDeclaredRealInputs() {
        EnvironmentManifest manifest = new EnvironmentManifest(1, "python", "main.py",
                List.of(new DependencySpec("torch", "2.5.1+cpu", HASH)), List.of(), IMAGE, "scientific");

        PreflightResult result = new EnvironmentManifestValidator().validate(manifest, Path.of("."));

        assertFalse(result.accepted());
        assertTrue(result.violations().stream().anyMatch(v -> v.code().equals("PYTHON_INPUT_MISSING")));
    }

    @Test
    void manifestDigestUsesCanonicalOrdering() {
        List<DependencySpec> firstDependencies = List.of(
                new DependencySpec("pillow", "10.4.0", HASH),
                new DependencySpec("numpy", "1.26.4", HASH));
        List<DependencySpec> secondDependencies = List.of(
                new DependencySpec("numpy", "1.26.4", HASH),
                new DependencySpec("pillow", "10.4.0", HASH));
        EnvironmentManifest first = new EnvironmentManifest(1, "python", "main.py",
                firstDependencies, List.of(), IMAGE, "scientific");
        EnvironmentManifest second = new EnvironmentManifest(1, "python", "main.py",
                secondDependencies, List.of(), IMAGE, "scientific");

        assertEquals(first.manifestDigest(), second.manifestDigest());
    }
}
