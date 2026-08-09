package com.reportai.runtime;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable administrator-owned catalog; it never fetches packages or contacts a network. */
public final class EnvironmentCatalog {
    private final Map<String, RuntimeImageProfile> profiles;
    private final Map<String, DependencySpec> dependencies;
    private final Map<String, CatalogDependency> catalogDependencies;

    public EnvironmentCatalog(Collection<RuntimeImageProfile> profiles, Collection<DependencySpec> dependencies) {
        this.profiles = indexProfiles(profiles);
        this.catalogDependencies = indexCatalogDependencies(legacyCatalogDependencies(dependencies));
        this.dependencies = projectDependencies(this.catalogDependencies);
        validateDependencyGraph(this.catalogDependencies);
    }

    public EnvironmentCatalog(Map<String, RuntimeImageProfile> profiles, Map<String, DependencySpec> dependencies) {
        this.profiles = normalizeProfiles(profiles);
        this.catalogDependencies = indexCatalogDependencies(legacyCatalogDependencies(
                dependencies == null ? List.of() : dependencies.values()));
        this.dependencies = projectDependencies(this.catalogDependencies);
        validateDependencyGraph(this.catalogDependencies);
    }

    private EnvironmentCatalog(Collection<RuntimeImageProfile> profiles,
                                Collection<CatalogDependency> dependencies, boolean graphAware) {
        this.profiles = indexProfiles(profiles);
        this.catalogDependencies = indexCatalogDependencies(dependencies);
        this.dependencies = projectDependencies(this.catalogDependencies);
        validateDependencyGraph(this.catalogDependencies);
    }

    public static EnvironmentCatalog of(List<RuntimeImageProfile> profiles, List<DependencySpec> dependencies) {
        return new EnvironmentCatalog(profiles, dependencies);
    }

    public static EnvironmentCatalog withDependencyGraph(List<RuntimeImageProfile> profiles,
                                                           List<CatalogDependency> dependencies) {
        return new EnvironmentCatalog(profiles, dependencies, true);
    }

    public RuntimeImageProfile profile(String language) {
        return profiles.get(language == null ? "" : language.trim().toLowerCase(Locale.ROOT));
    }

    public DependencySpec dependency(String name, String version) {
        if (name == null || version == null) return null;
        return dependencies.get(key(name, version));
    }

    public CatalogDependency catalogDependency(String name, String version) {
        if (name == null || version == null) return null;
        return catalogDependencies.get(key(name, version));
    }

    public Map<String, RuntimeImageProfile> profiles() {
        return profiles;
    }

    public Map<String, DependencySpec> dependencies() {
        return dependencies;
    }

    public Map<String, CatalogDependency> catalogDependencies() {
        return catalogDependencies;
    }

    /** Returns the requested packages and every catalog-approved transitive dependency. */
    public List<DependencySpec> resolveDependencyClosure(Collection<DependencySpec> requested) {
        if (requested == null || requested.isEmpty()) return List.of();
        Map<String, DependencySpec> resolved = new LinkedHashMap<>();
        List<DependencySpec> roots = requested.stream()
                .sorted(Comparator.comparing(DependencySpec::name).thenComparing(DependencySpec::version))
                .toList();
        for (DependencySpec root : roots) {
            collectClosure(root, resolved, new HashSet<>());
        }
        return resolved.values().stream()
                .sorted(Comparator.comparing(DependencySpec::name).thenComparing(DependencySpec::version))
                .toList();
    }

    /**
     * Resolves package-name intent to exact catalog coordinates.  A name may be
     * used only when the administrator catalog contains exactly one version;
     * multiple versions remain ambiguous and must be pinned by the caller.
     */
    public List<DependencySpec> resolveRequestedPackages(Collection<String> requested) {
        if (requested == null || requested.isEmpty()) return List.of();
        List<DependencySpec> result = new ArrayList<>();
        for (String raw : requested) {
            if (raw == null || raw.isBlank()) {
                throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.DEPENDENCY_UNPINNED,
                        "dependency name is blank");
            }
            String value = raw.trim().toLowerCase(Locale.ROOT);
            int split = value.indexOf("==");
            if (split >= 1) {
                result.add(new DependencySpec(value.substring(0, split), value.substring(split + 2)));
                continue;
            }
            List<DependencySpec> candidates = catalogDependencies.values().stream()
                    .map(CatalogDependency::dependency)
                    .filter(dependency -> dependency.name().equals(value))
                    .distinct()
                    .toList();
            if (candidates.size() != 1) {
                throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.DEPENDENCY_UNPINNED,
                        candidates.isEmpty() ? "dependency is not approved by the environment catalog: " + value
                                : "dependency has multiple approved versions and must be pinned: " + value);
            }
            result.add(candidates.get(0));
        }
        return List.copyOf(result);
    }

    private void collectClosure(DependencySpec requested, Map<String, DependencySpec> resolved, Set<String> visiting) {
        CatalogDependency catalogDependency = catalogDependencies.get(key(requested.name(), requested.version()));
        if (catalogDependency == null) {
            throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.DEPENDENCY_NOT_APPROVED,
                    "dependency is not approved by the environment catalog: "
                            + requested.name() + "==" + requested.version());
        }
        String coordinate = catalogDependency.key();
        if (!resolved.containsKey(coordinate)) {
            if (!visiting.add(coordinate)) {
                throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.CATALOG_INVALID,
                        "dependency graph contains a cycle at " + coordinate);
            }
            for (DependencyCoordinate required : catalogDependency.requires()) {
                CatalogDependency requiredEntry = catalogDependencies.get(required.key());
                if (requiredEntry == null) {
                    throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.CATALOG_INVALID,
                            "dependency graph references missing package: " + required.key());
                }
                collectClosure(requiredEntry.dependency(), resolved, visiting);
            }
            visiting.remove(coordinate);
            resolved.put(coordinate, catalogDependency.dependency());
        }
    }

    private static Map<String, RuntimeImageProfile> indexProfiles(Collection<RuntimeImageProfile> values) {
        if (values == null || values.isEmpty()) {
            throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.CATALOG_INVALID,
                    "environment catalog must contain a runtime profile");
        }
        Map<String, RuntimeImageProfile> result = new LinkedHashMap<>();
        for (RuntimeImageProfile profile : values) {
            if (profile == null || result.put(profile.language(), profile) != null) {
                throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.CATALOG_INVALID,
                        "environment catalog contains duplicate runtime profiles");
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, CatalogDependency> indexCatalogDependencies(Collection<CatalogDependency> values) {
        Map<String, CatalogDependency> result = new LinkedHashMap<>();
        if (values != null) {
            for (CatalogDependency dependency : values) {
                if (dependency == null || result.put(dependency.key(), dependency) != null) {
                    throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.CATALOG_INVALID,
                        "catalog dependencies require unique entries with sha256");
                }
            }
        }
        return Map.copyOf(result);
    }

    private static List<CatalogDependency> legacyCatalogDependencies(Collection<DependencySpec> values) {
        List<CatalogDependency> result = new ArrayList<>();
        if (values == null) return result;
        for (DependencySpec dependency : values) {
            try {
                result.add(new CatalogDependency(dependency));
            } catch (RuntimeException invalid) {
                throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.CATALOG_INVALID,
                        "catalog dependency is invalid: " + invalid.getMessage());
            }
        }
        return result;
    }

    private static Map<String, DependencySpec> projectDependencies(Map<String, CatalogDependency> values) {
        Map<String, DependencySpec> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, value.dependency()));
        return Map.copyOf(result);
    }

    private static Map<String, RuntimeImageProfile> normalizeProfiles(Map<String, RuntimeImageProfile> values) {
        if (values == null || values.isEmpty()) {
            throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.CATALOG_INVALID,
                    "environment catalog must contain a runtime profile");
        }
        return indexProfiles(values.values());
    }

    private static void validateDependencyGraph(Map<String, CatalogDependency> dependencies) {
        for (CatalogDependency dependency : dependencies.values()) {
            for (DependencyCoordinate required : dependency.requires()) {
                if (!dependencies.containsKey(required.key())) {
                    throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.CATALOG_INVALID,
                            "dependency graph references missing package: " + required.key());
                }
            }
        }
        Map<String, VisitState> states = new LinkedHashMap<>();
        for (String coordinate : dependencies.keySet()) {
            visit(coordinate, dependencies, states);
        }
    }

    private static void visit(String coordinate, Map<String, CatalogDependency> dependencies,
                              Map<String, VisitState> states) {
        VisitState state = states.get(coordinate);
        if (state == VisitState.VISITING) {
            throw new EnvironmentCatalogException(EnvironmentCatalogException.Code.CATALOG_INVALID,
                    "dependency graph contains a cycle at " + coordinate);
        }
        if (state == VisitState.VISITED) return;
        states.put(coordinate, VisitState.VISITING);
        for (DependencyCoordinate required : dependencies.get(coordinate).requires()) {
            visit(required.key(), dependencies, states);
        }
        states.put(coordinate, VisitState.VISITED);
    }

    private enum VisitState { VISITING, VISITED }

    private static String key(String name, String version) {
        return name.trim().toLowerCase(Locale.ROOT) + "==" + version.trim();
    }
}
