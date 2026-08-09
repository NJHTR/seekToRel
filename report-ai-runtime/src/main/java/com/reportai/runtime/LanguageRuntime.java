package com.reportai.runtime;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Immutable allow-listed runtime image and command definition. */
public record LanguageRuntime(String language, String image, Function<String, List<String>> commandBuilder) {
    private static final Pattern IMMUTABLE_IMAGE = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:/-]*@sha256:[a-fA-F0-9]{64}");

    public LanguageRuntime {
        language = requireToken(language, "language");
        image = requireToken(image, "image");
        if (!IMMUTABLE_IMAGE.matcher(image).matches()) {
            throw new IllegalArgumentException("runtime image must use an immutable sha256 digest");
        }
        commandBuilder = Objects.requireNonNull(commandBuilder, "commandBuilder");
    }

    public List<String> command(String entryPoint) {
        List<String> command = commandBuilder.apply(entryPoint);
        if (command == null || command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("runtime command must not be empty");
        }
        return List.copyOf(command);
    }

    /** Builds an ephemeral dependency command without accepting arbitrary shell input. */
    public List<String> command(String entryPoint, PythonDependencyRequest dependencies) {
        Objects.requireNonNull(dependencies, "dependencies");
        if (dependencies.isEmpty()) {
            return command(entryPoint);
        }
        if (!"python".equalsIgnoreCase(language)) {
            throw new IllegalArgumentException("dependencies are only supported for Python");
        }
        return PythonDependencyCommand.build(entryPoint, dependencies);
    }

    private static String requireToken(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
