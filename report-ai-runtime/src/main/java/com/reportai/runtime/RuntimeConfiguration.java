package com.reportai.runtime;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Runtime limits and the only images which the service is allowed to start. */
public record RuntimeConfiguration(
        Path workspaceRoot,
        Map<String, LanguageRuntime> languages,
        int maxArtifacts,
        long maxArtifactBytes,
        int maxCpuLimit,
        long maxMemoryBytes,
        java.time.Duration maxTimeout,
        long maxOutputBytes) {

    public RuntimeConfiguration {
        workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        if (languages == null || languages.isEmpty()) {
            throw new IllegalArgumentException("at least one runtime language is required");
        }
        Map<String, LanguageRuntime> normalized = new LinkedHashMap<>();
        languages.forEach((key, value) -> {
            if (key == null || value == null) {
                throw new IllegalArgumentException("language registry contains null");
            }
            normalized.put(key.toLowerCase(Locale.ROOT), value);
        });
        languages = Map.copyOf(normalized);
        if (maxArtifacts <= 0 || maxArtifactBytes <= 0 || maxCpuLimit <= 0 || maxMemoryBytes <= 0
                || maxTimeout == null || maxTimeout.isNegative() || maxTimeout.isZero() || maxOutputBytes <= 0) {
            throw new IllegalArgumentException("runtime limits must be positive");
        }
    }

    public LanguageRuntime language(String value) {
        return languages.get(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
    }

    public static RuntimeConfiguration productionDefaults(Path root) {
        Map<String, LanguageRuntime> registry = Map.of(
                "python", new LanguageRuntime("python", "python:3.12-slim", entry -> List.of("python", entry)),
                "java", new LanguageRuntime("java", "eclipse-temurin:21-jdk", RuntimeConfiguration::javaCommand),
                "cpp", new LanguageRuntime("cpp", "gcc:14", RuntimeConfiguration::cppCommand));
        return new RuntimeConfiguration(root, registry, 100, 50L * 1024 * 1024, 4,
                4L * 1024 * 1024 * 1024, java.time.Duration.ofMinutes(10), 20L * 1024 * 1024);
    }

    private static java.util.List<String> javaCommand(String entry) {
        String source = shellQuote(entry);
        String className = shellQuote(entry.substring(entry.lastIndexOf('/') + 1, entry.length() - ".java".length()));
        return java.util.List.of("sh", "-c", "javac --release 21 " + source + " && java " + className);
    }

    private static java.util.List<String> cppCommand(String entry) {
        String source = shellQuote(entry);
        String binary = "/tmp/reportai-program";
        return java.util.List.of("sh", "-c", "g++ -std=c++20 -O2 " + source + " -o " + binary + " && " + binary);
    }

    /** Shell is only used inside an allow-listed image; this quotes every non-safe character. */
    private static String shellQuote(String value) {
        if (value == null || value.isBlank() || value.length() > 256 || !value.matches("[A-Za-z0-9_./-]+")) {
            throw new IllegalArgumentException("entry point contains unsupported characters");
        }
        return "'" + value.replace("'", "'\\\"'\\\"'") + "'";
    }
}
