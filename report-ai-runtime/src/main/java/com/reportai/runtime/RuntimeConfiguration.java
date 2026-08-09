package com.reportai.runtime;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Runtime limits and the only images which the service is allowed to start. */
public record RuntimeConfiguration(
        Path workspaceRoot,
        Map<String, LanguageRuntime> languages,
        int maxArtifacts,
        long maxArtifactBytes,
        int maxCpuLimit,
        long maxMemoryBytes,
        java.time.Duration maxTimeout,
        long maxOutputBytes,
        Set<String> allowedPythonDependencies,
        boolean catalogLocked,
        Map<String, DependencySpec> approvedPythonCoordinates) {

    public RuntimeConfiguration(Path workspaceRoot, Map<String, LanguageRuntime> languages,
                                int maxArtifacts, long maxArtifactBytes, int maxCpuLimit,
                                long maxMemoryBytes, java.time.Duration maxTimeout, long maxOutputBytes) {
        this(workspaceRoot, languages, maxArtifacts, maxArtifactBytes, maxCpuLimit, maxMemoryBytes,
                maxTimeout, maxOutputBytes,
                Set.of("numpy", "pillow", "matplotlib", "pyyaml", "torch", "torchvision"),
                false, Map.of());
    }

    /** Compatibility constructor for custom allow-lists without a signed catalog. */
    public RuntimeConfiguration(Path workspaceRoot, Map<String, LanguageRuntime> languages,
                                int maxArtifacts, long maxArtifactBytes, int maxCpuLimit,
                                long maxMemoryBytes, java.time.Duration maxTimeout, long maxOutputBytes,
                                Set<String> allowedPythonDependencies) {
        this(workspaceRoot, languages, maxArtifacts, maxArtifactBytes, maxCpuLimit, maxMemoryBytes,
                maxTimeout, maxOutputBytes, allowedPythonDependencies, false, Map.of());
    }

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
        if (allowedPythonDependencies == null || allowedPythonDependencies.size() > 128
                || allowedPythonDependencies.stream().anyMatch(value -> value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}"))) {
            throw new IllegalArgumentException("Python dependency allow-list is invalid");
        }
        allowedPythonDependencies = allowedPythonDependencies.stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        approvedPythonCoordinates = approvedPythonCoordinates == null ? Map.of() : Map.copyOf(approvedPythonCoordinates);
        if (approvedPythonCoordinates.values().stream().anyMatch(value -> value == null || value.sha256() == null)) {
            throw new IllegalArgumentException("approved Python coordinates require sha256 hashes");
        }
        approvedPythonCoordinates = approvedPythonCoordinates.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> coordinate(entry.getValue().name(), entry.getValue().version()),
                        Map.Entry::getValue,
                        (left, right) -> {
                            throw new IllegalArgumentException("duplicate approved Python coordinate");
                        }));
    }

    public LanguageRuntime language(String value) {
        return languages.get(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Returns a copy whose allow-listed language images come from the signed
     * administrator catalog. Command builders remain platform-owned code; the
     * catalog can select an image but cannot inject a command.
     */
    public RuntimeConfiguration withCatalog(EnvironmentCatalog catalog) {
        if (catalog == null) {
            return this;
        }
        Map<String, LanguageRuntime> merged = new LinkedHashMap<>(languages);
        catalog.profiles().forEach((language, profile) -> {
            LanguageRuntime base = merged.get(language);
            if (base == null) {
                throw new IllegalArgumentException("catalog language is not enabled by the runtime: " + language);
            }
            merged.put(language, new LanguageRuntime(language, profile.imageDigest(), base.commandBuilder()));
        });
        java.util.Set<String> approvedDependencies = new java.util.HashSet<>(allowedPythonDependencies);
        catalog.dependencies().values().stream().map(DependencySpec::name).forEach(approvedDependencies::add);
        return new RuntimeConfiguration(workspaceRoot, merged, maxArtifacts, maxArtifactBytes, maxCpuLimit,
                maxMemoryBytes, maxTimeout, maxOutputBytes, approvedDependencies, true,
                catalog.dependencies());
    }

    public static RuntimeConfiguration productionDefaults(Path root) {
        Map<String, LanguageRuntime> registry = Map.of(
                "python", new LanguageRuntime("python",
                        "python@sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de",
                        entry -> List.of("python", entry)),
                "java", new LanguageRuntime("java",
                        "eclipse-temurin@sha256:b388ef0a5da6bf6a265f22a3b298e7a33000382bd1b9fe7a4124e99d1a2eb760",
                        RuntimeConfiguration::javaCommand),
                "cpp", new LanguageRuntime("cpp",
                        "gcc@sha256:1ea81e094f614fd2ed066316651dbac8eecb4d36add2ddd8a26151374c85c52c",
                        RuntimeConfiguration::cppCommand));
        return new RuntimeConfiguration(root, registry, 100, 50L * 1024 * 1024, 4,
                4L * 1024 * 1024 * 1024, java.time.Duration.ofMinutes(10), 20L * 1024 * 1024,
                Set.of("numpy", "pillow", "matplotlib", "pyyaml", "torch", "torchvision"),
                false, Map.of());
    }

    public void validatePythonDependencies(PythonDependencyRequest request) {
        if (request == null || request.isEmpty()) {
            return;
        }
        if (request.packages().stream().anyMatch(value -> !value.contains("=="))) {
            throw new IllegalArgumentException("Python dependencies must pin an exact version");
        }
        if (request.packageNames().stream().anyMatch(value -> !allowedPythonDependencies.contains(value))) {
            throw new IllegalArgumentException("Python dependency is not approved by the runtime policy");
        }
        if (catalogLocked) {
            if (request.resolvedDependencies().isEmpty()) {
                throw new IllegalArgumentException("Python dependencies must be resolved by the signed environment catalog");
            }
            for (DependencySpec dependency : request.resolvedDependencies()) {
                DependencySpec approved = approvedPythonCoordinates.get(coordinate(dependency.name(), dependency.version()));
                if (approved == null || !approved.equals(dependency)) {
                    throw new IllegalArgumentException("Python dependency is not approved by the signed environment catalog");
                }
            }
        }
    }

    private static String coordinate(String name, String version) {
        return name.toLowerCase(Locale.ROOT) + "==" + version;
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
