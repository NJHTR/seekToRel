package com.reportai.application.task;

import com.reportai.agent.Agent;
import com.reportai.agent.AgentContext;
import com.reportai.agent.AgentResult;
import com.reportai.runtime.EnvironmentManifest;
import com.reportai.runtime.EnvironmentManifestValidator;
import com.reportai.runtime.PreflightResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Durable, side-effect-free preflight step between code generation and runtime. */
final class EnvironmentPreflightAgent implements Agent {
    static final String TYPE = "preflight";
    private final EnvironmentManifestValidator validator;
    private final Path workspaceRoot;
    private final String workspaceId;
    private final Function<Object, EnvironmentManifest> decoder;

    EnvironmentPreflightAgent(EnvironmentManifestValidator validator, Path workspaceRoot,
                               String workspaceId, Function<Object, EnvironmentManifest> decoder) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        if (context == null) {
            return failed("ENV_MANIFEST_INVALID: preflight context is missing");
        }
        Object raw = context.state().get("environmentManifest");
        if (raw == null) {
            Object request = context.state().get("runtimeRequest");
            if (request instanceof Map<?, ?> values && values.get("dependencies") instanceof List<?> dependencies
                    && !dependencies.isEmpty()) {
                return failed("ENV_MANIFEST_REQUIRED: dependency execution requires a resolved manifest");
            }
            return new AgentResult(AgentResult.Status.COMPLETED,
                    Map.of("preflight", Map.of("accepted", true, "mode", "legacy-no-dependencies")),
                    "environment preflight passed (legacy no-dependency mode)");
        }
        final EnvironmentManifest manifest;
        try {
            manifest = raw instanceof EnvironmentManifest value ? value : decoder.apply(raw);
        } catch (RuntimeException invalid) {
            return failed("ENV_MANIFEST_INVALID: environment manifest is invalid");
        }
        PreflightResult result = validator.validate(manifest, workspaceRoot.resolve(workspaceId));
        if (!result.accepted()) {
            String code = result.violations().stream().map(PreflightResult.Violation::code)
                    .findFirst().orElse("ENV_MANIFEST_INVALID");
            List<Map<String, String>> violations = result.violations().stream()
                    .map(value -> Map.of("code", value.code(), "path", value.path(), "message", value.message()))
                    .toList();
            return new AgentResult(AgentResult.Status.FAILED,
                    Map.of("toolErrorCode", code, "preflight", Map.of("accepted", false,
                            "errorCode", code, "violations", violations)),
                    "ENV_" + code + ": environment preflight failed");
        }
        return new AgentResult(AgentResult.Status.COMPLETED,
                Map.of("environmentManifest", manifest, "environmentManifestDigest", manifest.manifestDigest(),
                        "preflight", Map.of("accepted", true, "manifestDigest", manifest.manifestDigest())),
                "environment preflight passed");
    }

    private static AgentResult failed(String message) {
        return new AgentResult(AgentResult.Status.FAILED,
                Map.of("toolErrorCode", "ENV_MANIFEST_INVALID",
                        "preflight", Map.of("accepted", false, "errorCode", "ENV_MANIFEST_INVALID")), message);
    }
}
