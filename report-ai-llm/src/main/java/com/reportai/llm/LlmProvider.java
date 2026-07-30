package com.reportai.llm;

import java.util.Set;

public interface LlmProvider extends ChatPort, ToolCallPort, VisionPort, EmbeddingPort, OcrPort {
    String providerId();

    default Set<Capability> capabilities() {
        return Set.of(Capability.CHAT);
    }

    default boolean supports(Capability capability) {
        return capability != null && capabilities().contains(capability);
    }

    @Override
    default ToolCallResponse toolCall(ToolCallRequest request) {
        throw new UnsupportedCapabilityException(providerId(), Capability.TOOL_CALL);
    }

    @Override
    default VisionResponse vision(VisionRequest request) {
        throw new UnsupportedCapabilityException(providerId(), Capability.VISION);
    }

    @Override
    default EmbeddingResponse embedding(EmbeddingRequest request) {
        throw new UnsupportedCapabilityException(providerId(), Capability.EMBEDDING);
    }

    @Override
    default OcrResponse ocr(OcrRequest request) {
        throw new UnsupportedCapabilityException(providerId(), Capability.OCR);
    }
}
