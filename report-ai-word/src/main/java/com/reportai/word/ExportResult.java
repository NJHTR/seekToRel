package com.reportai.word;

import java.util.List;

/** Result of a streamed export; no document bytes are retained in memory. */
public record ExportResult(int blocksWritten, int assetsWritten, List<ExportWarning> warnings) {
    public ExportResult {
        if (blocksWritten < 0 || assetsWritten < 0) {
            throw new IllegalArgumentException("counts cannot be negative");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
