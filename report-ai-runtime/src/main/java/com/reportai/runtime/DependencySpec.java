package com.reportai.runtime;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable, reproducible package coordinate resolved by the environment catalog. */
public record DependencySpec(String name, String version, String sha256) {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    private static final Pattern VERSION = Pattern.compile("[0-9][A-Za-z0-9.+!~-]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    public DependencySpec {
        name = normalizeName(name);
        if (version == null || !VERSION.matcher(version.trim()).matches()) {
            throw new IllegalArgumentException("dependency version must be an exact version");
        }
        version = version.trim();
        if (sha256 != null) {
            sha256 = sha256.trim().toLowerCase(Locale.ROOT);
            if (!SHA256.matcher(sha256).matches()) {
                throw new IllegalArgumentException("dependency sha256 must be 64 hexadecimal characters");
            }
        }
    }

    /** A wheel hash is optional for legacy requests, but required by strict manifest validation. */
    public DependencySpec(String name, String version) {
        this(name, version, null);
    }

    private static String normalizeName(String value) {
        Objects.requireNonNull(value, "dependency name");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!NAME.matcher(normalized).matches() || normalized.startsWith(".") || normalized.startsWith("-")) {
            throw new IllegalArgumentException("dependency name is invalid");
        }
        return normalized;
    }
}
