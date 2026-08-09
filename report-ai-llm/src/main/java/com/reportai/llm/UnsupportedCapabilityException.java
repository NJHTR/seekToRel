package com.reportai.llm;

public final class UnsupportedCapabilityException extends LlmException {
    private static final long serialVersionUID = 1L;

    public UnsupportedCapabilityException(String providerId, Capability capability) {
        super("LLM_CAPABILITY_UNSUPPORTED", "LLM provider does not support capability: "
                + capability + " (provider=" + (providerId == null ? "unknown" : providerId) + ")");
    }
}
