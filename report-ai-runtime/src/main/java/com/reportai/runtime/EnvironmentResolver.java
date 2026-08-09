package com.reportai.runtime;

import java.util.List;
import java.util.Objects;

/** Resolves an untrusted dependency request exclusively against an administrator catalog. */
public final class EnvironmentResolver {
    private final EnvironmentCatalog catalog;

    public EnvironmentResolver(EnvironmentCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public EnvironmentManifest resolve(String language, String entryPoint,
                                       PythonDependencyRequest request,
                                       List<InputRequirement> inputs) {
        RuntimeImageProfile profile = catalog.profile(language);
        if (profile == null) {
            throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.LANGUAGE_NOT_APPROVED,
                    "language is not approved by the environment catalog");
        }
        List<DependencySpec> requested;
        try {
            requested = request == null ? List.of() : catalog.resolveRequestedPackages(request.packages());
        } catch (EnvironmentCatalogException invalid) {
            throw invalid;
        } catch (IllegalArgumentException invalid) {
            throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.DEPENDENCY_UNPINNED,
                    "dependencies must use exact pinned versions");
        }
        List<DependencySpec> resolved = catalog.resolveDependencyClosure(requested);
        return new EnvironmentManifest(1, profile.language(), entryPoint, resolved, inputs,
                profile.imageDigest(), profile.capabilityProfile());
    }

    public EnvironmentManifest resolve(String language, String entryPoint,
                                       PythonDependencyRequest request) {
        return resolve(language, entryPoint, request, List.of());
    }
}
