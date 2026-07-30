package com.reportai.word;

import com.reportai.document.model.Document;

import java.io.OutputStream;

public interface WordBuilder {
    /**
     * Writes a DOCX to the supplied stream. The stream remains owned by the caller.
     */
    ExportResult build(Document document, OutputStream output, WordBuildOptions options);

    /** Backwards-compatible convenience method for callers that do not use assets. */
    default void build(Document document, OutputStream output) {
        build(document, output, WordBuildOptions.defaults());
    }
}
