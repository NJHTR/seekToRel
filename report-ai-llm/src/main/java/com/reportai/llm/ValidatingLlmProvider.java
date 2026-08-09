package com.reportai.llm;

import java.util.Objects;
import java.util.Set;

/** Decorator that enforces server-side request and response limits before provider I/O. */
public final class ValidatingLlmProvider implements LlmProvider {
    private final LlmProvider delegate;
    private final LlmRequestValidator validator;

    public ValidatingLlmProvider(LlmProvider delegate) {
        this(delegate, new DefaultLlmRequestValidator());
    }

    public ValidatingLlmProvider(LlmProvider delegate, LlmRequestValidator validator) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    @Override
    public String providerId() {
        return delegate.providerId();
    }

    @Override
    public Set<Capability> capabilities() {
        return Set.copyOf(delegate.capabilities());
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        validate(request, Capability.CHAT);
        ChatResponse response = delegate.chat(request);
        LlmResponseGuard.validate(request, response);
        return response;
    }

    @Override
    public ToolCallResponse toolCall(ToolCallRequest request) {
        validate(request, Capability.TOOL_CALL);
        ToolCallResponse response = delegate.toolCall(request);
        LlmResponseGuard.validate(request, response);
        validateUsage(request, response == null ? null : response.usage());
        return response;
    }

    @Override
    public VisionResponse vision(VisionRequest request) {
        validate(request, Capability.VISION);
        VisionResponse response = delegate.vision(request);
        validateUsage(request, response == null ? null : response.usage());
        return response;
    }

    @Override
    public EmbeddingResponse embedding(EmbeddingRequest request) {
        validate(request, Capability.EMBEDDING);
        EmbeddingResponse response = delegate.embedding(request);
        validateUsage(request, response == null ? null : response.usage());
        return response;
    }

    @Override
    public OcrResponse ocr(OcrRequest request) {
        validate(request, Capability.OCR);
        OcrResponse response = delegate.ocr(request);
        validateUsage(request, response == null ? null : response.usage());
        return response;
    }

    private void validate(LlmRequest request, Capability capability) {
        validator.validate(request);
        if (!supports(capability)) {
            throw new UnsupportedCapabilityException(providerId(), capability);
        }
    }

    private static void validateUsage(LlmRequest request, TokenUsage usage) {
        if (usage == null) {
            throw new LlmValidationException(java.util.List.of("provider returned no token usage"));
        }
        if (usage.outputTokens() > request.budget().maxOutputTokens()) {
            throw new LlmValidationException(java.util.List.of("response output tokens exceed requested budget"));
        }
    }
}
