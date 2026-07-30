package com.reportai.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Produces source code from a planner output.  It never writes files or invokes a
 * compiler; those side effects are delegated to {@link RuntimeAgent} and its Tool.
 */
public final class CoderAgent implements Agent {
    public static final String TYPE = "coder";
    public static final String DEFAULT_PLAN_KEY = "plan";
    public static final String DEFAULT_LANGUAGE_KEY = "language";
    public static final String DEFAULT_OUTPUT_KEY = "code";
    private static final String DEFAULT_PROMPT = """
            You are ReportAI's Coder Agent. Generate complete, deterministic source code
            that satisfies the supplied execution plan. Respect the requested language,
            avoid network access and host-specific paths, and include all imports and a
            runnable entry point. Return source code only, without Markdown fences or
            commentary. Never claim that code was executed.
            """;

    private final String planKey;
    private final String languageKey;
    private final String outputKey;
    private final String systemPrompt;

    public CoderAgent() {
        this(DEFAULT_PLAN_KEY, DEFAULT_LANGUAGE_KEY, DEFAULT_OUTPUT_KEY, DEFAULT_PROMPT);
    }

    public CoderAgent(String planKey, String languageKey, String outputKey, String systemPrompt) {
        this.planKey = require(planKey, "planKey");
        this.languageKey = require(languageKey, "languageKey");
        this.outputKey = require(outputKey, "outputKey");
        if (systemPrompt == null || systemPrompt.isBlank() || systemPrompt.length() > LlmAgentSupport.MAX_INPUT_CHARS) {
            throw new IllegalArgumentException("systemPrompt is invalid");
        }
        this.systemPrompt = systemPrompt.trim();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        if (context == null) {
            return LlmAgentSupport.missingContext("agent context is required");
        }
        Object plan = context.state().get(planKey);
        if (plan == null) {
            return LlmAgentSupport.missingContext("required state is missing: " + planKey);
        }
        Object language = context.state().get(languageKey);
        String languageText = language == null ? "unspecified" : LlmAgentSupport.render(language);
        String input = "language=" + languageText + "\nplan=" + LlmAgentSupport.render(plan);
        AgentResult result = LlmAgentSupport.call(context, TYPE, systemPrompt, input, outputKey);
        if (result.status() != AgentResult.Status.COMPLETED) {
            return result;
        }
        String source;
        try {
            source = normalizeCode(String.valueOf(result.outputs().get(outputKey)));
        } catch (IllegalArgumentException invalid) {
            return LlmAgentSupport.missingContext("generated code is invalid");
        }
        Map<String, Object> outputs = new LinkedHashMap<>(result.outputs());
        outputs.put(outputKey, source);
        outputs.put("codeLanguage", languageText);
        return new AgentResult(AgentResult.Status.COMPLETED, outputs, result.message());
    }

    private static String normalizeCode(String value) {
        String code = LlmAgentSupport.bound(value, LlmAgentSupport.MAX_OUTPUT_CHARS, "generated code").trim();
        if (code.startsWith("```") && code.endsWith("```")) {
            int firstLine = code.indexOf('\n');
            if (firstLine > 0) {
                code = code.substring(firstLine + 1, code.length() - 3).trim();
            }
        }
        if (code.isBlank()) {
            throw new IllegalArgumentException("generated code must not be blank");
        }
        return code;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
