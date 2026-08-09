package com.reportai.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvironmentResolverTest {
    private static final String IMAGE = "python@sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de";
    private static final String HASH = "a".repeat(64);
    private final EnvironmentResolver resolver = new EnvironmentResolver(new EnvironmentCatalog(
            List.of(new RuntimeImageProfile("python", IMAGE, "scientific")),
            List.of(new DependencySpec("numpy", "1.26.4", HASH))));

    @Test
    void resolvesLegacyRequestToLockedManifest() {
        EnvironmentManifest result = resolver.resolve("PYTHON", "main.py",
                new PythonDependencyRequest(List.of("numpy==1.26.4")));
        assertEquals(IMAGE, result.imageDigest());
        assertEquals(HASH, result.dependencies().get(0).sha256());
        assertEquals("scientific", result.capabilityProfile());
    }

    @Test
    void rejectsUnknownPackageAndLanguage() {
        EnvironmentCatalogException dependency = assertThrows(EnvironmentCatalogException.class,
                () -> resolver.resolve("python", "main.py",
                        new PythonDependencyRequest(List.of("pillow==10.0.0"))));
        assertEquals(EnvironmentCatalogException.Code.DEPENDENCY_NOT_APPROVED, dependency.code());
        EnvironmentCatalogException language = assertThrows(EnvironmentCatalogException.class,
                () -> resolver.resolve("ruby", "main.rb", PythonDependencyRequest.empty()));
        assertEquals(EnvironmentCatalogException.Code.LANGUAGE_NOT_APPROVED, language.code());
    }

    @Test
    void rejectsCatalogDependencyWithoutHash() {
        assertThrows(EnvironmentCatalogException.class, () -> new EnvironmentCatalog(
                List.of(new RuntimeImageProfile("python", IMAGE)),
                List.of(new DependencySpec("numpy", "1.26.4"))));
    }

    @Test
    void resolvesCompleteTransitiveDependencyClosure() {
        String hashB = "b".repeat(64);
        String hashC = "c".repeat(64);
        EnvironmentCatalog catalog = EnvironmentCatalog.withDependencyGraph(
                List.of(new RuntimeImageProfile("python", IMAGE)),
                List.of(
                        new CatalogDependency(new DependencySpec("numpy", "1.26.4", HASH),
                                List.of(new DependencyCoordinate("packaging", "24.1"))),
                        new CatalogDependency(new DependencySpec("packaging", "24.1", hashB),
                                List.of(new DependencyCoordinate("pyparsing", "3.1.2"))),
                        new CatalogDependency(new DependencySpec("pyparsing", "3.1.2", hashC))));

        EnvironmentManifest manifest = new EnvironmentResolver(catalog).resolve("python", "main.py",
                new PythonDependencyRequest(List.of("numpy==1.26.4")));

        assertEquals(List.of("numpy", "packaging", "pyparsing"),
                manifest.dependencies().stream().map(DependencySpec::name).sorted().toList());
    }

    @Test
    void resolvesAnUnpinnedImportIntentOnlyWhenCatalogVersionIsUnique() {
        EnvironmentCatalog catalog = EnvironmentCatalog.withDependencyGraph(
                List.of(new RuntimeImageProfile("python", IMAGE, "scientific")),
                List.of(new CatalogDependency(new DependencySpec("torch", "2.5.1+cpu", HASH))));

        EnvironmentManifest manifest = new EnvironmentResolver(catalog).resolve("python", "main.py",
                new PythonDependencyRequest(List.of("torch")), List.of());

        assertEquals(List.of("torch==2.5.1+cpu"), manifest.dependencies().stream()
                .map(value -> value.name() + "==" + value.version()).toList());
    }

    @Test
    void rejectsAnUnpinnedPackageWhenCatalogHasMultipleVersions() {
        EnvironmentCatalog catalog = new EnvironmentCatalog(
                List.of(new RuntimeImageProfile("python", IMAGE, "scientific")),
                List.of(new DependencySpec("numpy", "1.26.4", HASH),
                        new DependencySpec("numpy", "2.0.0", "b".repeat(64))));

        EnvironmentCatalogException failure = assertThrows(EnvironmentCatalogException.class,
                () -> new EnvironmentResolver(catalog).resolve("python", "main.py",
                        new PythonDependencyRequest(List.of("numpy"))));
        assertEquals(EnvironmentCatalogException.Code.DEPENDENCY_UNPINNED, failure.code());
    }

    @Test
    void rejectsMissingAndCyclicCatalogDependencies() {
        assertThrows(EnvironmentCatalogException.class, () -> EnvironmentCatalog.withDependencyGraph(
                List.of(new RuntimeImageProfile("python", IMAGE)),
                List.of(new CatalogDependency(new DependencySpec("numpy", "1.26.4", HASH),
                        List.of(new DependencyCoordinate("missing", "1.0.0"))))));

        assertThrows(EnvironmentCatalogException.class, () -> EnvironmentCatalog.withDependencyGraph(
                List.of(new RuntimeImageProfile("python", IMAGE)),
                List.of(
                        new CatalogDependency(new DependencySpec("a", "1.0.0", HASH),
                                List.of(new DependencyCoordinate("b", "1.0.0"))),
                        new CatalogDependency(new DependencySpec("b", "1.0.0", "b".repeat(64)),
                                List.of(new DependencyCoordinate("a", "1.0.0"))))));
    }
}
