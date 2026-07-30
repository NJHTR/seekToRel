package com.reportai.agent;

import com.reportai.llm.ChatMessage;
import com.reportai.llm.ChatRequest;
import com.reportai.llm.ChatResponse;
import com.reportai.llm.LlmProvider;

import java.util.List;
import java.util.Map;

/**
 * Shared safety boundary for domain agents that use an LLM.  Prompts and responses are
 * bounded before entering the agent state so a provider cannot turn one task into an
 * unbounded memory or logging operation.
 */
final class LlmAgentSupport {
    static final int MAX_INPUT_CHARS = 128 * 1024;
    static final int MAX_OUTPUT_CHARS = 512 * 1024;

    private LlmAgentSupport() {
    }

    static AgentResult missingContext(String message) {
        return new AgentResult(AgentResult.Status.FAILED, Map.of(), message);
    }

    static AgentResult complete(String key, String value, String message) {
        return new AgentResult(AgentResult.Status.COMPLETED, Map.of(key, value), message);
    }

    static AgentResult call(AgentContext context, String type, String systemPrompt,
                            String userInput, String outputKey) {
        if (context == null || context.llmProvider() == null) {
            return missingContext("LLM provider is not configured for agent: " + type);
        }
        String boundedInput;
        try {
            boundedInput = bound(userInput, MAX_INPUT_CHARS, "agent input");
        } catch (IllegalArgumentException invalid) {
            return missingContext(invalid.getMessage());
        }
        try {
            ChatResponse response = context.llmProvider().chat(new ChatRequest(null, List.of(
                    new ChatMessage(ChatMessage.Role.SYSTEM, systemPrompt),
                    new ChatMessage(ChatMessage.Role.USER, boundedInput)),
                    Map.of(), null, context.taskId()));
            if (response == null || response.content() == null || response.content().isBlank()) {
                return missingContext("LLM returned an empty response for agent: " + type);
            }
            String output = bound(response.content(), MAX_OUTPUT_CHARS, "LLM response");
            return complete(outputKey, output, "agent completed: " + type);
        } catch (RuntimeException failure) {
            // Never expose provider URLs, request payloads or credentials through a task message.
            return missingContext("LLM execution failed for agent: " + type);
        }
    }

    static String bound(String value, int maxChars, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > maxChars) {
            throw new IllegalArgumentException(field + " exceeds the permitted size");
        }
        return value;
    }

    static String render(Object value) {
        StringBuilder result = new StringBuilder(Math.min(MAX_INPUT_CHARS, 8_192));
        appendRendered(result, value, 0);
        return result.toString();
    }

    private static void appendRendered(StringBuilder result, Object value, int depth) {
        if (result.length() >= MAX_INPUT_CHARS) {
            return;
        }
        if (depth >= 16) {
            appendBounded(result, "[depth-limit]");
            return;
        }
        if (value == null) {
            appendBounded(result, "");
        } else if (value instanceof String text) {
            appendBounded(result, text);
        } else if (value instanceof Map<?, ?> map) {
            appendBounded(result, "{");
            map.entrySet().stream()
                    .filter(entry -> entry.getKey() instanceof String)
                    .sorted((left, right) -> String.valueOf(left.getKey()).compareTo(String.valueOf(right.getKey())))
                    .forEach(entry -> {
                        if (result.length() < MAX_INPUT_CHARS && result.length() > 1) {
                            appendBounded(result, ", ");
                        }
                        appendBounded(result, String.valueOf(entry.getKey()) + "=");
                        appendRendered(result, entry.getValue(), depth + 1);
                    });
            appendBounded(result, "}");
        } else if (value instanceof Iterable<?> values) {
            appendBounded(result, "[");
            for (Object item : values) {
                if (result.length() >= MAX_INPUT_CHARS) {
                    break;
                }
                if (result.length() > 1) {
                    appendBounded(result, ", ");
                }
                appendRendered(result, item, depth + 1);
            }
            appendBounded(result, "]");
        } else {
            appendBounded(result, String.valueOf(value));
        }
    }

    private static void appendBounded(StringBuilder result, String value) {
        if (value == null || result.length() >= MAX_INPUT_CHARS) {
            return;
        }
        int remaining = MAX_INPUT_CHARS - result.length();
        result.append(value, 0, Math.min(remaining, value.length()));
    }
}
