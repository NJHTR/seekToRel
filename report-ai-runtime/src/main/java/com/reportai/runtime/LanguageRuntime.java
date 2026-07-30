package com.reportai.runtime;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Immutable allow-listed runtime image and command definition. */
public record LanguageRuntime(String language, String image, Function<String, List<String>> commandBuilder) {
    public LanguageRuntime {
        language = requireToken(language, "language");
        image = requireToken(image, "image");
        commandBuilder = Objects.requireNonNull(commandBuilder, "commandBuilder");
    }

    public List<String> command(String entryPoint) {
        List<String> command = commandBuilder.apply(entryPoint);
        if (command == null || command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("runtime command must not be empty");
        }
        return List.copyOf(command);
    }

    private static String requireToken(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
