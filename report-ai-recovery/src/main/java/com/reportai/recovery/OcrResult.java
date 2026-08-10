package com.reportai.recovery;

public record OcrResult(String textDigest, double confidence, boolean accepted, double threshold) implements RecoveryPayload {
    public static final double DEFAULT_THRESHOLD = 0.80d;
    public OcrResult(String textDigest, double confidence, boolean accepted) { this(textDigest, confidence, accepted, DEFAULT_THRESHOLD); }
    public OcrResult {
        textDigest = PayloadRules.text(textDigest, "textDigest", 128);
        if (Double.isNaN(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence out of range");
        if (Double.isNaN(threshold) || threshold < 0 || threshold > 1) throw new IllegalArgumentException("threshold out of range");
        if (accepted && confidence < threshold) throw new IllegalArgumentException("accepted OCR is below confidence threshold");
    }
    public static OcrResult withThreshold(String textDigest, double confidence, boolean accepted, double threshold) { return new OcrResult(textDigest, confidence, accepted, threshold); }
}
