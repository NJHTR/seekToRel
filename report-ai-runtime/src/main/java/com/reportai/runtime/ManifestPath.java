package com.reportai.runtime;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

final class ManifestPath {
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_./-]{1,256}");
    private ManifestPath() { }

    static String requireRelative(String value, String label) {
        if (value == null || value.isBlank() || !SAFE.matcher(value).matches() || value.indexOf('\\') >= 0
                || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(label + " must be a safe relative path");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.equals("..") || segment.isBlank()) {
                throw new IllegalArgumentException(label + " must be a safe relative path");
            }
        }
        try {
            Path path = Paths.get(value).normalize();
            if (path.isAbsolute() || path.startsWith("..") || path.toString().isBlank()) {
                throw new IllegalArgumentException(label + " must be a safe relative path");
            }
            return path.toString().replace('\\', '/');
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException(label + " must be a safe relative path", ex);
        }
    }
}
