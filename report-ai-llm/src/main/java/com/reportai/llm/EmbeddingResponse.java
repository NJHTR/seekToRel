package com.reportai.llm;

import java.util.List;

public record EmbeddingResponse(String providerId, String model, List<List<Double>> vectors,
                                TokenUsage usage) {
    public EmbeddingResponse {
        providerId = providerId == null ? "" : providerId.trim();
        model = model == null ? "" : model.trim();
        vectors = vectors == null ? List.of() : vectors.stream().map(vector ->
                vector == null ? List.<Double>of() : List.copyOf(vector)).toList();
        usage = usage == null ? TokenUsage.empty() : usage;
    }
}
