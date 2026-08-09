package com.reportai.word;

import com.reportai.document.model.Document;
import com.reportai.review.ReviewEngine;
import com.reportai.review.ReviewResult;

import java.io.OutputStream;
import java.util.Objects;

/** Default implementation: review first, then stream the export exactly once. */
public final class DefaultWordReleaseService implements WordReleaseService {
    private final ReviewEngine reviewEngine;
    private final WordBuilder wordBuilder;

    public DefaultWordReleaseService(ReviewEngine reviewEngine, WordBuilder wordBuilder) {
        this.reviewEngine = Objects.requireNonNull(reviewEngine, "reviewEngine");
        this.wordBuilder = Objects.requireNonNull(wordBuilder, "wordBuilder");
    }

    @Override
    public ReleaseResult release(Document document, OutputStream output, WordBuildOptions options) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(options, "options");
        ReviewResult review = reviewEngine.review(document);
        if (!review.passed()) {
            return new ReleaseResult(ReleaseStatus.REJECTED, review, null);
        }
        ExportResult export = wordBuilder.build(document, output, options);
        return new ReleaseResult(ReleaseStatus.RELEASED, review, export);
    }
}
