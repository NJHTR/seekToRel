package com.reportai.review;

import java.util.List;

public record ReviewResult(boolean passed, List<ReviewFinding> findings) {
    public ReviewResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
