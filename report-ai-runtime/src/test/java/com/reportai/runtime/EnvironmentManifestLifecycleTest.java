package com.reportai.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the immutable manifest boundary used by the task
 * handler.  In particular, a standard-library-only Python program still needs
 * a digest: the task aggregate cannot be locked after preflight otherwise.
 */
class EnvironmentManifestLifecycleTest {
    private static final String IMAGE =
            "python@sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de";

    @Test
    void legacyNoDependencyRequestStillProducesAStableDigest() throws Exception {
        EnvironmentManifest manifest = EnvironmentManifest.fromLegacy(
                "python", "main.py", PythonDependencyRequest.empty(), IMAGE);
        Path workspace = Files.createTempDirectory("reportai-manifest-lifecycle-");

        PreflightResult result = new EnvironmentManifestValidator().validate(manifest, workspace);

        assertTrue(result.accepted(), () -> result.violations().toString());
        assertEquals(64, manifest.manifestDigest().length());
        assertEquals(manifest.manifestDigest(), new EnvironmentManifest(
                manifest.schemaVersion(), manifest.language(), manifest.entryPoint(),
                manifest.dependencies(), manifest.inputs(), manifest.imageDigest(),
                manifest.capabilityProfile()).manifestDigest());
    }

    @Test
    void digestChangesWhenExecutionIdentityChanges() {
        EnvironmentManifest standard = EnvironmentManifest.fromLegacy(
                "python", "main.py", PythonDependencyRequest.empty(), IMAGE);
        EnvironmentManifest alternateEntryPoint = EnvironmentManifest.fromLegacy(
                "python", "run.py", PythonDependencyRequest.empty(), IMAGE);

        assertNotEquals(standard.manifestDigest(), alternateEntryPoint.manifestDigest());
    }

    @Test
    void providedDigestIsNormalizedAndTamperingIsRejected() {
        EnvironmentManifest source = new EnvironmentManifest(1, "python", "main.py", List.of(),
                List.of(), IMAGE, "default");
        EnvironmentManifest uppercase = new EnvironmentManifest(1, "python", "main.py", List.of(),
                List.of(), IMAGE, "default", source.manifestDigest().toUpperCase());

        assertEquals(source.manifestDigest(), uppercase.manifestDigest());
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentManifest(1, "python", "main.py",
                List.of(), List.of(), IMAGE, "default", "f".repeat(64)));
    }
}
