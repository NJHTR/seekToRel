package com.reportai.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A declarative, reproducible Python dependency request.
 *
 * <p>Dependencies are resolved by the sandbox image's read-only wheelhouse.  The
 * request deliberately carries package coordinates rather than a pip command;
 * callers cannot select an index, shell flags, or host paths.</p>
 */
public record PythonDependencyRequest(List<String> packages, List<DependencySpec> resolvedDependencies) {
    private static final Pattern SPEC = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}(?:==[0-9][A-Za-z0-9.+!~-]{0,63})?");
    public static final int MAX_PACKAGES = 16;

    public PythonDependencyRequest {
        packages = packages == null ? List.of() : List.copyOf(packages);
        resolvedDependencies = resolvedDependencies == null ? List.of() : List.copyOf(resolvedDependencies);
        if (packages.size() > MAX_PACKAGES) {
            throw new IllegalArgumentException("too many Python dependencies");
        }
        List<String> normalized = new ArrayList<>(packages.size());
        for (String value : packages) {
            if (value == null || value.isBlank() || !SPEC.matcher(value).matches()) {
                throw new IllegalArgumentException("invalid Python dependency coordinate");
            }
            normalized.add(value.trim().toLowerCase(Locale.ROOT));
        }
        packages = List.copyOf(normalized);
        if (resolvedDependencies.size() > MAX_PACKAGES
                || resolvedDependencies.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("resolved Python dependencies are invalid");
        }
        if (!resolvedDependencies.isEmpty()) {
            List<String> resolved = resolvedDependencies.stream()
                    .map(value -> (value.name() + "==" + value.version()).toLowerCase(Locale.ROOT)).toList();
            if (!resolved.equals(packages)) {
                throw new IllegalArgumentException("resolved dependencies do not match package coordinates");
            }
        }
    }

    public PythonDependencyRequest(List<String> packages) {
        this(packages, List.of());
    }

    public static PythonDependencyRequest empty() {
        return new PythonDependencyRequest(List.of(), List.of());
    }

    public boolean isEmpty() {
        return packages.isEmpty();
    }

    public List<String> packageNames() {
        return packages.stream().map(value -> {
            int version = value.indexOf("==");
            return version < 0 ? value : value.substring(0, version);
        }).toList();
    }

    /** Converts the legacy coordinates to manifest specs without fabricating artifact hashes. */
    public List<DependencySpec> toDependencySpecs() {
        return packages.stream().map(value -> {
            int split = value.indexOf("==");
            if (split < 1 || split == value.length() - 2) {
                throw new IllegalArgumentException("Python dependencies must pin an exact version");
            }
            return new DependencySpec(value.substring(0, split), value.substring(split + 2));
        }).toList();
    }

    public static PythonDependencyRequest fromManifest(EnvironmentManifest manifest) {
        if (manifest == null) {
            return empty();
        }
        return new PythonDependencyRequest(manifest.dependencies().stream()
                .map(value -> value.name() + "==" + value.version()).toList(), manifest.dependencies());
    }
}
