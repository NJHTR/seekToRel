package com.reportai.runtime;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Exact package coordinate used by the administrator-owned dependency graph. */
public record DependencyCoordinate(String name, String version) {
    private static final Pattern VERSION = Pattern.compile("[0-9][A-Za-z0-9.+!~-]{0,63}");

    public DependencyCoordinate {
        Objects.requireNonNull(name, "dependency name");
        name = name.trim().toLowerCase(Locale.ROOT);
        if (name.isBlank()) {
            throw new IllegalArgumentException("dependency name is required");
        }
        Objects.requireNonNull(version, "dependency version");
        version = version.trim();
        if (!VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("dependency version must be exact");
        }
    }

    public static DependencyCoordinate of(DependencySpec dependency) {
        Objects.requireNonNull(dependency, "dependency");
        return new DependencyCoordinate(dependency.name(), dependency.version());
    }

    public String key() {
        return name + "==" + version;
    }
}
