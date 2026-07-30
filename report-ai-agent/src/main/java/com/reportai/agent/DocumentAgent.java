package com.reportai.agent;

import com.reportai.tool.Tool;
import com.reportai.tool.ToolContext;
import com.reportai.tool.ToolInput;
import com.reportai.tool.ToolResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces a document-fill draft from canonical document data and completed outputs.
 * It does not mutate a {@code Document}. When a writer tool is configured, the draft is
 * handed to that tool through the registry; otherwise the application layer can validate
 * the draft and pass a canonical document to the review-gated Word release service.
 */
public final class DocumentAgent implements Agent {
    public static final String TYPE = "document";
    public static final String DEFAULT_OUTPUT_KEY = "documentDraft";
    private static final List<String> DEFAULT_INPUT_KEYS = List.of(
            "document", "plan", "code", "execution", "answers");
    private static final String DEFAULT_PROMPT = """
            You are ReportAI's Document Agent. Prepare a complete document-fill draft from
            the canonical document and upstream outputs. Preserve the original section
            order, answer every required question, include source code and verified runtime
            results, and reference only existing artifacts. Return a structured, concise
            document patch or content map, not a DOCX binary, and never invent an execution
            result.
            """;

    private final List<String> inputKeys;
    private final String outputKey;
    private final String systemPrompt;
    private final String writerToolName;

    public DocumentAgent() {
        this(DEFAULT_INPUT_KEYS, DEFAULT_OUTPUT_KEY, DEFAULT_PROMPT, null);
    }

    /** Creates a document agent that also delegates the final write to a registered tool. */
    public DocumentAgent(String writerToolName) {
        this(DEFAULT_INPUT_KEYS, DEFAULT_OUTPUT_KEY, DEFAULT_PROMPT, writerToolName);
    }

    public DocumentAgent(List<String> inputKeys, String outputKey, String systemPrompt) {
        this(inputKeys, outputKey, systemPrompt, null);
    }

    public DocumentAgent(List<String> inputKeys, String outputKey, String systemPrompt, String writerToolName) {
        if (inputKeys == null || inputKeys.isEmpty() || inputKeys.size() > 32
                || inputKeys.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 128)) {
            throw new IllegalArgumentException("inputKeys are invalid");
        }
        this.inputKeys = List.copyOf(inputKeys);
        if (outputKey == null || outputKey.isBlank() || outputKey.length() > 128) {
            throw new IllegalArgumentException("outputKey is invalid");
        }
        this.outputKey = outputKey.trim();
        if (systemPrompt == null || systemPrompt.isBlank() || systemPrompt.length() > LlmAgentSupport.MAX_INPUT_CHARS) {
            throw new IllegalArgumentException("systemPrompt is invalid");
        }
        this.systemPrompt = systemPrompt.trim();
        if (writerToolName != null && (writerToolName.isBlank() || writerToolName.length() > 128)) {
            throw new IllegalArgumentException("writerToolName is invalid");
        }
        this.writerToolName = writerToolName == null ? null : writerToolName.trim();
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
        if (inputKeys.contains("document") && context.state().get("document") == null) {
            return LlmAgentSupport.missingContext("required state is missing: document");
        }
        List<String> parts = new ArrayList<>();
        boolean found = false;
        for (String key : inputKeys) {
            Object value = context.state().get(key);
            if (value != null) {
                found = true;
                parts.add(key + "=" + LlmAgentSupport.render(value));
            }
        }
        if (!found) {
            return LlmAgentSupport.missingContext("document agent input state is missing");
        }
        AgentResult draft = LlmAgentSupport.call(context, TYPE, systemPrompt,
                String.join("\n", parts), outputKey);
        if (draft.status() != AgentResult.Status.COMPLETED || writerToolName == null) {
            return draft;
        }
        if (context.toolRegistry() == null || context.tenantId() == null) {
            return LlmAgentSupport.missingContext("document writer tool context is incomplete");
        }
        Tool writer;
        try {
            writer = context.toolRegistry().find(writerToolName).orElse(null);
        } catch (RuntimeException failure) {
            return LlmAgentSupport.missingContext("document writer tool lookup failed");
        }
        if (writer == null) {
            return LlmAgentSupport.missingContext("document writer tool is not registered: " + writerToolName);
        }
        ToolResult written;
        try {
            written = writer.execute(new ToolContext(context.taskId(), context.tenantId(),
                    String.valueOf(context.state().getOrDefault("workspaceId", ""))),
                    new ToolInput(Map.of("draft", draft.outputs().get(outputKey))));
        } catch (RuntimeException failure) {
            return LlmAgentSupport.missingContext("document writer tool failed");
        }
        if (written == null || written.status() != ToolResult.Status.SUCCEEDED) {
            return LlmAgentSupport.missingContext(written == null
                    ? "document writer tool returned no result" : written.publicMessage());
        }
        Map<String, Object> outputs = new LinkedHashMap<>(draft.outputs());
        outputs.put("documentArtifact", written.outputs());
        return new AgentResult(AgentResult.Status.COMPLETED, outputs, written.publicMessage());
    }
}
