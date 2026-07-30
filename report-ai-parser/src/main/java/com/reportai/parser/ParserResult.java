package com.reportai.parser;

import com.reportai.document.model.Document;
import com.reportai.parser.asset.ParsedAsset;

import java.util.List;
import java.util.Objects;

/** Immutable parser output. The source bytes are deliberately not retained here. */
public record ParserResult(Document document, long inputBytes, List<ParserWarning> warnings,
                           List<ParsedAsset> assets) {
    public ParserResult {
        document = Objects.requireNonNull(document, "document");
        if (inputBytes <= 0) {
            throw new IllegalArgumentException("inputBytes must be positive");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        assets = assets == null ? List.of() : List.copyOf(assets);
    }

    /** Backward-compatible constructor for parsers that do not extract assets. */
    public ParserResult(Document document, long inputBytes, List<ParserWarning> warnings) {
        this(document, inputBytes, warnings, List.of());
    }
}
