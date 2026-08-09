package com.reportai.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** A hash-pinned catalog package and its exact, already-resolved dependencies. */
public record CatalogDependency(DependencySpec dependency, List<DependencyCoordinate> requires) {
    public CatalogDependency {
        dependency = Objects.requireNonNull(dependency, "dependency");
        if (dependency.sha256() == null) {
            throw new IllegalArgumentException("catalog dependency requires a sha256");
        }
        List<DependencyCoordinate> normalized = requires == null ? List.of() : new ArrayList<>(requires);
        if (normalized.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("catalog dependency requirements cannot be null");
        }
        normalized.sort(Comparator.comparing(DependencyCoordinate::name)
                .thenComparing(DependencyCoordinate::version));
        if (normalized.stream().distinct().count() != normalized.size()) {
            throw new IllegalArgumentException("catalog dependency requirements must be unique");
        }
        if (normalized.contains(DependencyCoordinate.of(dependency))) {
            throw new IllegalArgumentException("catalog dependency cannot require itself");
        }
        requires = List.copyOf(normalized);
    }

    public CatalogDependency(DependencySpec dependency) {
        this(dependency, List.of());
    }

    public String key() {
        return DependencyCoordinate.of(dependency).key();
    }
}
