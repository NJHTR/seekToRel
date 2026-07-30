package com.reportai.agent;

import com.reportai.llm.ChatMessage;
import com.reportai.llm.ChatRequest;
import com.reportai.llm.ChatResponse;
import com.reportai.llm.LlmProvider;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A side-effect-free agent that asks the configured LLM to produce one state value. */
public final class LlmPromptAgent implements Agent {
    private final String type;
    private final String outputKey;
    private final String systemPrompt;
    private final String inputKey;

    public LlmPromptAgent(String type, String outputKey, String systemPrompt, String inputKey) {
        this.type = require(type, "type");
        this.outputKey = require(outputKey, "outputKey");
        this.systemPrompt = require(systemPrompt, "systemPrompt");
        this.inputKey = require(inputKey, "inputKey");
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        if (context == null || context.llmProvider() == null) {
            return new AgentResult(AgentResult.Status.FAILED, Map.of(), "LLM provider is not configured");
        }
        Object input = context.state().get(inputKey);
        if (input == null) {
            return new AgentResult(AgentResult.Status.FAILED, Map.of(), "required state is missing: " + inputKey);
        }
        LlmProvider provider = Objects.requireNonNull(context.llmProvider());
        ChatResponse response = provider.chat(new ChatRequest(null, List.of(
                new ChatMessage(ChatMessage.Role.SYSTEM, systemPrompt),
                new ChatMessage(ChatMessage.Role.USER, String.valueOf(input))), Map.of()));
        return new AgentResult(AgentResult.Status.COMPLETED, Map.of(outputKey, response.content()),
                "agent completed: " + type);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
