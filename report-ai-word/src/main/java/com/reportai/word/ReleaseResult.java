package com.reportai.word;

import com.reportai.review.ReviewResult;

import java.util.Objects;

/** Outcome of a review-gated DOCX release. */
public record ReleaseResult(ReleaseStatus status, ReviewResult review, ExportResult export) {
    public ReleaseResult {
        status = Objects.requireNonNull(status, "status");
        review = Objects.requireNonNull(review, "review");
        if (status == ReleaseStatus.RELEASED && export == null) {
            throw new IllegalArgumentException("released result must contain export details");
        }
        if (status == ReleaseStatus.REJECTED && export != null) {
            throw new IllegalArgumentException("rejected result must not contain export details");
        }
    }

    public boolean released() {
        return status == ReleaseStatus.RELEASED;
    }
}
