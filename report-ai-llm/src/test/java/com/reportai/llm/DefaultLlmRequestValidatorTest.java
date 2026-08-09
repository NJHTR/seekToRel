package com.reportai.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultLlmRequestValidatorTest {
    @Test
    void allowsProviderGenerationControlsContainingTokens() {
        ChatRequest request = new ChatRequest("test-model",
                List.of(new ChatMessage(ChatMessage.Role.USER, "generate code")),
                Map.of("reasoning_effort", "medium", "max_completion_tokens", 2048),
                LlmRequestBudget.safeDefaults(), "request-1");

        assertDoesNotThrow(() -> new DefaultLlmRequestValidator().validate(request));
    }

    @Test
    void rejectsCredentialTokenOptions() {
        ChatRequest request = new ChatRequest("test-model",
                List.of(new ChatMessage(ChatMessage.Role.USER, "hello")),
                Map.of("refresh_token", "secret-value"),
                LlmRequestBudget.safeDefaults(), "request-1");

        assertThrows(LlmValidationException.class,
                () -> new DefaultLlmRequestValidator().validate(request));
    }
}
