package com.reportai.review.precheck;

import com.reportai.common.contract.Sha256Digest;
import java.util.*;

public record ContextProjection(List<ContextCandidate> selected, int totalChars, int estimatedTokens, Sha256Digest digest) {
    public ContextProjection { selected = selected == null ? List.of() : List.copyOf(selected); if (totalChars < 0 || estimatedTokens < 0) throw new IllegalArgumentException("context metrics invalid"); digest = Objects.requireNonNull(digest, "digest"); }
}
