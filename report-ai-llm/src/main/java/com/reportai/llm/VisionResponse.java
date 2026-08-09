package com.reportai.llm;

import java.util.List;
import java.util.Map;

public record VisionResponse(String providerId, String model, String content, List<String> labels,
                             TokenUsage usage, Map<String, Object> metadata) {
    public VisionResponse {
        providerId = providerId == null ? "" : providerId.trim();
        model = model == null ? "" : model.trim();
        content = content == null ? "" : content;
        labels = labels == null ? List.of() : List.copyOf(labels);
        usage = usage == null ? TokenUsage.empty() : usage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
