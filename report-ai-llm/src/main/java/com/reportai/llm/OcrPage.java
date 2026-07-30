package com.reportai.llm;

public record OcrPage(int pageNumber, String text, double confidence) {
    public OcrPage {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        text = text == null ? "" : text;
        if (text.length() > 10_000_000) {
            throw new IllegalArgumentException("OCR text is too long");
        }
        if (Double.isNaN(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }
}
