package com.reportai.agent;

import com.reportai.document.model.Document;
import com.reportai.review.CompletenessReviewEngine;
import com.reportai.review.ReviewEngine;
import com.reportai.review.ReviewFinding;
import com.reportai.review.ReviewResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs the deterministic review gate against a canonical document.  A failed review
 * intentionally returns FAILED so a release workflow cannot accidentally export an
 * incomplete report.
 */
public final class ReviewerAgent implements Agent {
    public static final String TYPE = "reviewer";
    public static final String DEFAULT_INPUT_KEY = "document";
    public static final String DEFAULT_OUTPUT_KEY = "reviewResult";
    private static final int MAX_FINDINGS = 256;

    private final ReviewEngine reviewEngine;
    private final String inputKey;
    private final String outputKey;

    public ReviewerAgent() {
        this(new CompletenessReviewEngine());
    }

    public ReviewerAgent(ReviewEngine reviewEngine) {
        this(reviewEngine, DEFAULT_INPUT_KEY, DEFAULT_OUTPUT_KEY);
    }

    public ReviewerAgent(ReviewEngine reviewEngine, String inputKey, String outputKey) {
        this.reviewEngine = Objects.requireNonNull(reviewEngine, "reviewEngine");
        this.inputKey = require(inputKey, "inputKey");
        this.outputKey = require(outputKey, "outputKey");
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        if (context == null || context.tenantId() == null) {
            return LlmAgentSupport.missingContext("review context is incomplete");
        }
        Object raw = context.state().get(inputKey);
        if (!(raw instanceof Document document)) {
            return LlmAgentSupport.missingContext("review input must be a canonical document: " + inputKey);
        }
        if (!context.tenantId().equals(document.tenantId())) {
            return LlmAgentSupport.missingContext("review document does not belong to the task tenant");
        }
        ReviewResult review;
        try {
            review = reviewEngine.review(document);
        } catch (RuntimeException failure) {
            return LlmAgentSupport.missingContext("document review failed");
        }
        List<ReviewFinding> findings = review.findings().size() > MAX_FINDINGS
                ? List.copyOf(review.findings().subList(0, MAX_FINDINGS)) : review.findings();
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put(outputKey, new ReviewResult(review.passed(), findings));
        outputs.put("reviewPassed", review.passed());
        outputs.put("reviewFindings", findings);
        String message = review.passed() ? "document review passed" : "document review rejected";
        return new AgentResult(review.passed() ? AgentResult.Status.COMPLETED : AgentResult.Status.FAILED,
                outputs, message);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
