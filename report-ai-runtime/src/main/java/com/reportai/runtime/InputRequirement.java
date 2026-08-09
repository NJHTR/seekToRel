package com.reportai.runtime;

import java.util.Locale;
import java.util.regex.Pattern;

/** A bounded workspace input required before an execution can start. */
public record InputRequirement(String relativePath, Kind kind, long maxBytes, String sha256) {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    public enum Kind { FILE, DIRECTORY }

    public InputRequirement {
        relativePath = ManifestPath.requireRelative(relativePath, "input path");
        kind = kind == null ? Kind.FILE : kind;
        if (maxBytes <= 0 || maxBytes > 10L * 1024 * 1024 * 1024) {
            throw new IllegalArgumentException("input maxBytes is outside the permitted range");
        }
        if (sha256 != null) {
            sha256 = sha256.trim().toLowerCase(Locale.ROOT);
            if (!SHA256.matcher(sha256).matches()) {
                throw new IllegalArgumentException("input sha256 must be 64 hexadecimal characters");
            }
        }
    }

    public InputRequirement(String relativePath, long maxBytes) {
        this(relativePath, Kind.FILE, maxBytes, null);
    }

    public InputRequirement(String relativePath, long maxBytes, String sha256) {
        this(relativePath, Kind.FILE, maxBytes, sha256);
    }

    public InputRequirement(String relativePath, Kind kind, long maxBytes) {
        this(relativePath, kind, maxBytes, null);
    }
}
