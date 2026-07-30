package com.reportai.runtime;

import java.util.Map;

public record ExecutionRequest(String language, String entryPoint, Map<String, String> environment,
                               ExecutionPolicy policy) {
    public ExecutionRequest {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("language must not be blank");
        }
        if (entryPoint == null || entryPoint.isBlank()) {
            throw new IllegalArgumentException("entryPoint must not be blank");
        }
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        policy = policy == null ? ExecutionPolicy.safeDefaults() : policy;
    }
}
