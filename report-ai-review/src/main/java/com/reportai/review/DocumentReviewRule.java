package com.reportai.review;

import com.reportai.document.model.Document;

import java.util.List;

/** A deterministic, side-effect-free review rule. */
@FunctionalInterface
public interface DocumentReviewRule {
    List<ReviewFinding> evaluate(Document document);
}
