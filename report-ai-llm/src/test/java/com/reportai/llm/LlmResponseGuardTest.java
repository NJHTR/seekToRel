package com.reportai.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmResponseGuardTest {
    private static final ToolDefinition TOOL = new ToolDefinition("python.run", "run python",
            Map.of("type", "object",
                    "properties", Map.of("source", Map.of("type", "string")),
                    "required", List.of("source"), "additionalProperties", false));

    @Test
    void acceptsDeclaredToolWithSchemaCompliantArguments() {
        assertDoesNotThrow(() -> LlmResponseGuard.validate(request(2), response(
                new ToolCall("call-1", "python.run", Map.of("source", "print(1)")))));
    }

    @Test
    void rejectsUndeclaredToolAndDuplicateCallId() {
        ToolCallRequest request = request(2);
        LlmValidationException failure = assertThrows(LlmValidationException.class,
                () -> LlmResponseGuard.validate(request, response(
                        new ToolCall("call-1", "shell.exec", Map.of()),
                        new ToolCall("call-1", "python.run", Map.of("source", "print(1)")))));

        assertTrue(failure.violations().stream().anyMatch(message -> message.contains("not declared")));
        assertTrue(failure.violations().stream().anyMatch(message -> message.contains("duplicated")));
    }

    @Test
    void rejectsExcessCallsAndSchemaViolations() {
        LlmValidationException failure = assertThrows(LlmValidationException.class,
                () -> LlmResponseGuard.validate(request(1), response(
                        new ToolCall("call-1", "python.run", Map.of("source", 1)),
                        new ToolCall("call-2", "python.run", Map.of("extra", "value")))));

        assertTrue(failure.violations().stream().anyMatch(message -> message.contains("call count")));
        assertTrue(failure.violations().stream().anyMatch(message -> message.contains("invalid type")));
        assertTrue(failure.violations().stream().anyMatch(message -> message.contains("not allowed")));
        assertTrue(failure.violations().stream().anyMatch(message -> message.contains("required")));
    }

    @Test
    void rejectsNonFiniteNumericArguments() {
        LlmValidationException failure = assertThrows(LlmValidationException.class,
                () -> LlmResponseGuard.validate(request(1), response(
                        new ToolCall("call-1", "python.run", Map.of("source", "print(1)", "value", Double.NaN)))));

        assertTrue(failure.violations().stream().anyMatch(message -> message.contains("finite")));
    }

    @Test
    void validatingProviderAppliesToolCallGuardBeforeReturning() {
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() {
                return "test";
            }

            @Override
            public Set<Capability> capabilities() {
                return Set.of(Capability.TOOL_CALL);
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                return new ChatResponse("test", "test-model", "", "stop");
            }

            @Override
            public ToolCallResponse toolCall(ToolCallRequest request) {
                return response(new ToolCall("call-1", "shell.exec", Map.of()));
            }
        };

        assertThrows(LlmValidationException.class,
                () -> new ValidatingLlmProvider(provider).toolCall(request(1)));
    }

    @Test
    void acceptsProviderReasoningUsageAboveVisibleOutputBudget() {
        ChatRequest request = new ChatRequest("test-model",
                List.of(new ChatMessage(ChatMessage.Role.USER, "generate code")), Map.of(),
                new LlmRequestBudget(100, 2, java.time.Duration.ofSeconds(5)), "chat-usage-test");
        ChatResponse response = new ChatResponse("test", "test-model", "print('ok')", "stop",
                new TokenUsage(10, 100, 110), Map.of());

        assertDoesNotThrow(() -> LlmResponseGuard.validate(request, response));
    }

    @Test
    void rejectsOversizedChatContentEvenWhenUsageIsMissing() {
        ChatRequest request = new ChatRequest("test-model",
                List.of(new ChatMessage(ChatMessage.Role.USER, "generate code")), Map.of(),
                new LlmRequestBudget(100, 2, java.time.Duration.ofSeconds(5)), "chat-size-test");
        ChatResponse response = new ChatResponse("test", "test-model", "x".repeat(512 * 1024 + 1), "stop");

        LlmValidationException failure = assertThrows(LlmValidationException.class,
                () -> LlmResponseGuard.validate(request, response));

        assertTrue(failure.violations().stream().anyMatch(message -> message.contains("content exceeds")));
    }

    @Test
    void rejectsDuplicateToolDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> new ToolCallRequest("test-model",
                List.of(new ChatMessage(ChatMessage.Role.USER, "run")), List.of(TOOL, TOOL), 1,
                Map.of(), LlmRequestBudget.safeDefaults(), "request-1"));
    }

    private static ToolCallRequest request(int maxToolCalls) {
        return new ToolCallRequest("test-model", List.of(new ChatMessage(ChatMessage.Role.USER, "run")),
                List.of(TOOL), maxToolCalls, Map.of(), LlmRequestBudget.safeDefaults(), "request-1");
    }

    private static ToolCallResponse response(ToolCall... calls) {
        return new ToolCallResponse("test", "test-model", "", List.of(calls), "tool_calls",
                TokenUsage.empty(), Map.of());
    }
}
