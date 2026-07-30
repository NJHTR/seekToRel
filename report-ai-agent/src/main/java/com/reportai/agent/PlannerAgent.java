package com.reportai.agent;

/**
 * Turns the canonical document (or an upstream document analysis) into a bounded
 * execution plan.  Planning is deliberately side-effect free; all execution is left
 * to later tool-backed agents.
 */
public final class PlannerAgent implements Agent {
    public static final String TYPE = "planner";
    public static final String DEFAULT_INPUT_KEY = "document";
    public static final String DEFAULT_OUTPUT_KEY = "plan";
    private static final String DEFAULT_PROMPT = """
            You are ReportAI's Planner Agent. Analyze the supplied canonical document and
            produce a concise, ordered execution plan. Identify required code language,
            files, charts, questions, and document sections. Do not execute code, call
            tools, invent completed outputs, or return Markdown fences. Include explicit
            validation checkpoints and keep the plan deterministic and reproducible.
            """;

    private final String inputKey;
    private final String outputKey;
    private final String systemPrompt;

    public PlannerAgent() {
        this(DEFAULT_INPUT_KEY, DEFAULT_OUTPUT_KEY, DEFAULT_PROMPT);
    }

    public PlannerAgent(String inputKey, String outputKey, String systemPrompt) {
        this.inputKey = require(inputKey, "inputKey");
        this.outputKey = require(outputKey, "outputKey");
        this.systemPrompt = requirePrompt(systemPrompt);
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
        Object document = context.state().get(inputKey);
        if (document == null) {
            return LlmAgentSupport.missingContext("required state is missing: " + inputKey);
        }
        return LlmAgentSupport.call(context, TYPE, systemPrompt,
                LlmAgentSupport.render(document), outputKey);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }

    private static String requirePrompt(String value) {
        if (value == null || value.isBlank() || value.length() > LlmAgentSupport.MAX_INPUT_CHARS) {
            throw new IllegalArgumentException("systemPrompt is invalid");
        }
        return value.trim();
    }
}
