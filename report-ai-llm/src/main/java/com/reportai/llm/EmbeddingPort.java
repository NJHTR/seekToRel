package com.reportai.llm;

public interface EmbeddingPort {
    EmbeddingResponse embedding(EmbeddingRequest request);
}
