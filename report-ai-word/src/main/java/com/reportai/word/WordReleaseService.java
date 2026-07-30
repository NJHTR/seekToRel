package com.reportai.word;

import com.reportai.document.model.Document;

import java.io.OutputStream;

/** Review-gated export port used by the application layer. */
public interface WordReleaseService {
    ReleaseResult release(Document document, OutputStream output, WordBuildOptions options);

    default ReleaseResult release(Document document, OutputStream output) {
        return release(document, output, WordBuildOptions.defaults());
    }
}
