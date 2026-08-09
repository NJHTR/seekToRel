package com.reportai.word;

import java.util.List;

/** A deterministic, local edit to a retained DOCX template. */
public record TemplatePatch(String anchor, String answer, List<String> figureAssetKeys) {
    public TemplatePatch {
        if (anchor == null || anchor.isBlank() || anchor.length() > 8_192) {
            throw new IllegalArgumentException("template patch anchor is required");
        }
        answer = answer == null ? "" : answer.strip();
        figureAssetKeys = figureAssetKeys == null ? List.of() : List.copyOf(figureAssetKeys);
    }

    public TemplatePatch(String anchor, String answer) {
        this(anchor, answer, List.of());
    }
}
