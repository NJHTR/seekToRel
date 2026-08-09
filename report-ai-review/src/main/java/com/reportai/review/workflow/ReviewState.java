package com.reportai.review.workflow;

/** States of the bounded two-review workflow. */
public enum ReviewState {
    FIRST_PASS_PENDING,
    ACCEPTANCE,
    REJECTION,
    SECOND_REVIEW_PENDING,
    RECOVERY_PENDING,
    REENTRY_PENDING,
    APPROVED,
    REJECTED,
    EXHAUSTED;

    public boolean terminal() {
        return this == APPROVED || this == REJECTED || this == EXHAUSTED;
    }
}
