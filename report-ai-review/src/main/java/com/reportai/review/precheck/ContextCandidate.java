package com.reportai.review.precheck;

import com.reportai.common.contract.EvidenceRef;
import java.util.Objects;

public record ContextCandidate(String key, String text, int priority, EvidenceRef evidence) {
    public ContextCandidate {
        key = bounded(key, "key", 128); text = bounded(text, "text", 100_000);
        if (priority < 0 || priority > 1_000) throw new IllegalArgumentException("priority out of range");
        evidence = Objects.requireNonNull(evidence, "evidence");
    }
    private static String bounded(String value, String field, int max) {
        Objects.requireNonNull(value, field); var n = value.strip();
        if (n.isBlank() || n.length() > max || n.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(field + " is invalid");
        return n;
    }
}
