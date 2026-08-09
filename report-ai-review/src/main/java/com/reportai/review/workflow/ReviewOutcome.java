package com.reportai.review.workflow;

import java.util.Objects;

/** Result of a command, including whether it was a replay of a prior idempotent command. */
public record ReviewOutcome(ReviewCase reviewCase, ReviewEvent event, boolean duplicate) {
    public ReviewOutcome {
        reviewCase = Objects.requireNonNull(reviewCase, "reviewCase");
        event = Objects.requireNonNull(event, "event");
    }
}
