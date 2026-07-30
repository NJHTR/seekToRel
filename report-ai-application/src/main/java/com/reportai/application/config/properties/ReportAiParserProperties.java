package com.reportai.application.config.properties;

import com.reportai.parser.security.ParserLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Resource limits applied before Apache POI parses an uploaded archive. */
@Validated
@ConfigurationProperties(prefix = "report-ai.parser")
public class ReportAiParserProperties {
    private long maxInputBytes = 20L * 1024 * 1024;
    private int maxZipEntries = 2_000;
    private long maxUncompressedBytes = 100L * 1024 * 1024;
    private long maxSingleEntryBytes = 50L * 1024 * 1024;
    private long maxXmlEntryBytes = 10L * 1024 * 1024;
    private int maxBlocks = 10_000;
    private int maxTextChars = 1_000_000;
    private int maxCompressionRatio = 100;

    public ParserLimits toParserLimits() {
        return new ParserLimits(maxInputBytes, maxZipEntries, maxUncompressedBytes,
                maxSingleEntryBytes, maxXmlEntryBytes, maxBlocks, maxTextChars,
                maxCompressionRatio);
    }

    public long getMaxInputBytes() {
        return maxInputBytes;
    }

    public void setMaxInputBytes(long maxInputBytes) {
        this.maxInputBytes = maxInputBytes;
    }

    public int getMaxZipEntries() {
        return maxZipEntries;
    }

    public void setMaxZipEntries(int maxZipEntries) {
        this.maxZipEntries = maxZipEntries;
    }

    public long getMaxUncompressedBytes() {
        return maxUncompressedBytes;
    }

    public void setMaxUncompressedBytes(long maxUncompressedBytes) {
        this.maxUncompressedBytes = maxUncompressedBytes;
    }

    public long getMaxSingleEntryBytes() {
        return maxSingleEntryBytes;
    }

    public void setMaxSingleEntryBytes(long maxSingleEntryBytes) {
        this.maxSingleEntryBytes = maxSingleEntryBytes;
    }

    public long getMaxXmlEntryBytes() {
        return maxXmlEntryBytes;
    }

    public void setMaxXmlEntryBytes(long maxXmlEntryBytes) {
        this.maxXmlEntryBytes = maxXmlEntryBytes;
    }

    public int getMaxBlocks() {
        return maxBlocks;
    }

    public void setMaxBlocks(int maxBlocks) {
        this.maxBlocks = maxBlocks;
    }

    public int getMaxTextChars() {
        return maxTextChars;
    }

    public void setMaxTextChars(int maxTextChars) {
        this.maxTextChars = maxTextChars;
    }

    public int getMaxCompressionRatio() {
        return maxCompressionRatio;
    }

    public void setMaxCompressionRatio(int maxCompressionRatio) {
        this.maxCompressionRatio = maxCompressionRatio;
    }
}
