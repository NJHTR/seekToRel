package com.reportai.review;

import com.reportai.document.model.Document;

public interface ReviewEngine {
    ReviewResult review(Document document);
}
