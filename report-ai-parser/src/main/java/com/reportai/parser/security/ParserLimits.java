package com.reportai.parser.security;

/** Resource limits enforced before Apache POI sees an uploaded archive. */
public record ParserLimits(long maxInputBytes,
                           int maxZipEntries,
                           long maxUncompressedBytes,
                           long maxSingleEntryBytes,
                           long maxXmlEntryBytes,
                           int maxBlocks,
                           int maxTextChars,
                           int maxCompressionRatio,
                           int maxAssets,
                           long maxAssetBytes,
                           long maxTotalAssetBytes) {
    public ParserLimits {
        if (maxInputBytes <= 0 || maxZipEntries <= 0 || maxUncompressedBytes <= 0
                || maxSingleEntryBytes <= 0 || maxXmlEntryBytes <= 0 || maxBlocks <= 0
                || maxTextChars <= 0 || maxCompressionRatio <= 0 || maxAssets <= 0
                || maxAssetBytes <= 0 || maxTotalAssetBytes <= 0) {
            throw new IllegalArgumentException("parser limits must be positive");
        }
        if (maxSingleEntryBytes > maxUncompressedBytes || maxXmlEntryBytes > maxSingleEntryBytes
                || maxAssetBytes > maxSingleEntryBytes || maxTotalAssetBytes > maxUncompressedBytes
                || maxAssetBytes > maxTotalAssetBytes) {
            throw new IllegalArgumentException("entry limits are inconsistent");
        }
    }

    /** Compatibility constructor retained for existing configuration binders. */
    public ParserLimits(long maxInputBytes, int maxZipEntries, long maxUncompressedBytes,
                        long maxSingleEntryBytes, long maxXmlEntryBytes, int maxBlocks,
                        int maxTextChars, int maxCompressionRatio) {
        this(maxInputBytes, maxZipEntries, maxUncompressedBytes, maxSingleEntryBytes,
                maxXmlEntryBytes, maxBlocks, maxTextChars, maxCompressionRatio,
                256, Math.min(maxSingleEntryBytes, 8L * 1024 * 1024),
                Math.min(maxUncompressedBytes, 40L * 1024 * 1024));
    }

    public static ParserLimits docxDefaults() {
        return new ParserLimits(
                20L * 1024 * 1024,
                2_000,
                100L * 1024 * 1024,
                50L * 1024 * 1024,
                10L * 1024 * 1024,
                10_000,
                1_000_000,
                100,
                256,
                8L * 1024 * 1024,
                40L * 1024 * 1024);
    }
}
