package com.reportai.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Locked, content-addressed description of one executable task environment. */
public record EnvironmentManifest(int schemaVersion, String language, String entryPoint,
                                  List<DependencySpec> dependencies, List<InputRequirement> inputs,
                                  String imageDigest, String capabilityProfile, String manifestDigest) {
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9./:_-]+@sha256:[0-9a-fA-F]{64}");
    private static final Pattern CAPABILITY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public EnvironmentManifest(int schemaVersion, String language, String entryPoint,
                               List<DependencySpec> dependencies, List<InputRequirement> inputs,
                               String imageDigest, String capabilityProfile) {
        this(schemaVersion, language, entryPoint, dependencies, inputs, imageDigest, capabilityProfile, null);
    }

    public EnvironmentManifest(String language, String entryPoint, List<DependencySpec> dependencies,
                               List<InputRequirement> inputs, String imageDigest, String capabilityProfile) {
        this(1, language, entryPoint, dependencies, inputs, imageDigest, capabilityProfile, null);
    }

    public EnvironmentManifest {
        if (schemaVersion <= 0) throw new IllegalArgumentException("manifest schemaVersion must be positive");
        if (language == null || language.isBlank() || language.length() > 32) {
            throw new IllegalArgumentException("manifest language is invalid");
        }
        language = language.trim().toLowerCase(java.util.Locale.ROOT);
        entryPoint = ManifestPath.requireRelative(entryPoint, "entryPoint");
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        if (dependencies.size() > 128 || inputs.size() > 256) {
            throw new IllegalArgumentException("manifest contains too many entries");
        }
        if (dependencies.stream().map(DependencySpec::name).distinct().count() != dependencies.size()
                || inputs.stream().map(InputRequirement::relativePath).distinct().count() != inputs.size()) {
            throw new IllegalArgumentException("manifest contains duplicate entries");
        }
        if (imageDigest == null || !IMAGE.matcher(imageDigest.trim()).matches()) {
            throw new IllegalArgumentException("runtime image must be digest pinned");
        }
        imageDigest = imageDigest.trim();
        capabilityProfile = capabilityProfile == null || capabilityProfile.isBlank()
                ? "default" : capabilityProfile.trim();
        if (!CAPABILITY.matcher(capabilityProfile).matches()) {
            throw new IllegalArgumentException("manifest capability profile is invalid");
        }
        String expected = canonicalDigest(schemaVersion, language, entryPoint, dependencies, inputs,
                imageDigest, capabilityProfile);
        if (manifestDigest != null && !expected.equalsIgnoreCase(manifestDigest.trim())) {
            throw new IllegalArgumentException("manifest digest does not match manifest contents");
        }
        manifestDigest = expected;
    }

    /** Adapts the pre-M2 dependency list without inventing wheel hashes. */
    public static EnvironmentManifest fromLegacy(String language, String entryPoint,
                                                 PythonDependencyRequest request, String imageDigest) {
        List<DependencySpec> specs = request == null ? List.of() : request.packages().stream().map(value -> {
            int split = value.indexOf("==");
            if (split < 1 || split == value.length() - 2) {
                throw new IllegalArgumentException("legacy dependencies must pin an exact version");
            }
            return new DependencySpec(value.substring(0, split), value.substring(split + 2));
        }).toList();
        return new EnvironmentManifest(1, language, entryPoint, specs, List.of(), imageDigest, "default");
    }

    private static String canonicalDigest(int schemaVersion, String language, String entryPoint,
                                          List<DependencySpec> dependencies, List<InputRequirement> inputs,
                                          String imageDigest, String capabilityProfile) {
        // Keep the digest independent of Java record formatting or map iteration
        // order.  This is a small canonical-JSON encoder: fixed field order,
        // sorted arrays, UTF-8 and no optional manifestDigest field.
        StringBuilder value = new StringBuilder(512).append('{');
        field(value, "capabilityProfile", capabilityProfile).append(',');
        value.append("\"dependencies\":[");
        dependencies.stream().sorted(java.util.Comparator.comparing(DependencySpec::name)
                        .thenComparing(DependencySpec::version)
                        .thenComparing(d -> d.sha256() == null ? "" : d.sha256()))
                .forEachOrdered(d -> {
                    if (value.charAt(value.length() - 1) != '[') value.append(',');
                    value.append('{');
                    field(value, "name", d.name()).append(',');
                    field(value, "sha256", d.sha256() == null ? "" : d.sha256()).append(',');
                    field(value, "version", d.version());
                    value.append('}');
                });
        value.append("],");
        field(value, "entryPoint", entryPoint).append(',');
        field(value, "imageDigest", imageDigest).append(',');
        value.append("\"inputs\":[");
        inputs.stream().sorted(java.util.Comparator.comparing(InputRequirement::relativePath)
                        .thenComparing(i -> i.kind().name())
                        .thenComparingLong(InputRequirement::maxBytes)
                        .thenComparing(i -> i.sha256() == null ? "" : i.sha256()))
                .forEachOrdered(i -> {
                    if (value.charAt(value.length() - 1) != '[') value.append(',');
                    value.append('{');
                    field(value, "kind", i.kind().name()).append(',');
                    value.append("\"maxBytes\":").append(i.maxBytes()).append(',');
                    field(value, "relativePath", i.relativePath()).append(',');
                    field(value, "sha256", i.sha256() == null ? "" : i.sha256());
                    value.append('}');
                });
        value.append("],");
        field(value, "language", language).append(',');
        value.append("\"schemaVersion\":").append(schemaVersion).append('}');
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static StringBuilder field(StringBuilder value, String name, String text) {
        value.append('"').append(name).append("\":");
        appendJsonString(value, text);
        return value;
    }

    private static void appendJsonString(StringBuilder value, String text) {
        value.append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> value.append("\\\"");
                case '\\' -> value.append("\\\\");
                case '\b' -> value.append("\\b");
                case '\f' -> value.append("\\f");
                case '\n' -> value.append("\\n");
                case '\r' -> value.append("\\r");
                case '\t' -> value.append("\\t");
                default -> {
                    if (character < 0x20) {
                        value.append(String.format("\\u%04x", (int) character));
                    } else {
                        value.append(character);
                    }
                }
            }
        }
        value.append('"');
    }
}
