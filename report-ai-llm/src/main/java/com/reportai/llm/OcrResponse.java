package com.reportai.llm;

import java.util.List;

public record OcrResponse(String providerId, String model, List<OcrPage> pages, TokenUsage usage) {
    public OcrResponse {
        providerId = providerId == null ? "" : providerId.trim();
        model = model == null ? "" : model.trim();
        pages = pages == null ? List.of() : List.copyOf(pages);
        usage = usage == null ? TokenUsage.empty() : usage;
    }
}
