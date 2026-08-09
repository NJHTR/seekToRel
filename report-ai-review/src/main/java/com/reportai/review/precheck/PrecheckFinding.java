package com.reportai.review.precheck;

import com.reportai.common.contract.EvidenceRef;
import java.util.*;

public record PrecheckFinding(PrecheckStage stage, String ruleId, PrecheckSeverity severity,
                              String code, String safeMessage, List<EvidenceRef> evidence,
                              int scoreImpact) implements Comparable<PrecheckFinding> {
    public PrecheckFinding {
        stage = Objects.requireNonNull(stage, "stage"); ruleId = text(ruleId, "ruleId", 64); severity = Objects.requireNonNull(severity, "severity");
        code = text(code, "code", 64); safeMessage = text(safeMessage, "safeMessage", 256);
        evidence = evidence == null ? List.of() : List.copyOf(evidence); if (evidence.size() > 32) throw new IllegalArgumentException("too many evidence refs");
        if (scoreImpact < 0 || scoreImpact > 100) throw new IllegalArgumentException("scoreImpact out of range");
    }
    private static String text(String value, String field, int max) { Objects.requireNonNull(value, field); var n = value.strip(); if (n.isBlank() || n.length() > max || n.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(field + " is invalid"); return n; }
    @Override public int compareTo(PrecheckFinding other) { return Comparator.comparing((PrecheckFinding f) -> f.stage().ordinal()).thenComparing(PrecheckFinding::ruleId).thenComparing(PrecheckFinding::code).compare(this, other); }
}
