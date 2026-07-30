package com.reportai.word;

/** Safe warning emitted during export. It intentionally contains logical block identifiers only. */
public record ExportWarning(String code, String message, int blockIndex) {
    public ExportWarning {
        if (code == null || code.isBlank() || message == null || message.isBlank() || blockIndex < -1) {
            throw new IllegalArgumentException("invalid export warning");
        }
    }
}
