package com.reportai.recovery;
public record OcrResult(String textDigest, double confidence, boolean accepted) implements RecoveryPayload { public OcrResult { textDigest=PayloadRules.text(textDigest,"textDigest",128); if(Double.isNaN(confidence)||confidence<0||confidence>1) throw new IllegalArgumentException("confidence out of range"); } }
