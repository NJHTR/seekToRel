package com.reportai.review.precheck;

import com.reportai.common.contract.Sha256Digest;
import java.util.Objects;

public record ContextSummary(String key, Sha256Digest evidenceDigest, int priority, int chars, Sha256Digest textDigest) {
    public ContextSummary {
        key = Objects.requireNonNull(key, "key"); evidenceDigest = Objects.requireNonNull(evidenceDigest, "evidenceDigest"); textDigest = Objects.requireNonNull(textDigest, "textDigest");
        if (key.isBlank() || priority < 0 || chars < 0) throw new IllegalArgumentException("context summary is invalid");
    }
}
