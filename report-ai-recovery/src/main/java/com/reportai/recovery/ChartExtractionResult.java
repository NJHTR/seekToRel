package com.reportai.recovery;
public record ChartExtractionResult(String resultDigest, boolean complete, int pointCount) implements RecoveryPayload { public ChartExtractionResult { resultDigest=PayloadRules.text(resultDigest,"resultDigest",128); if(pointCount<0||pointCount>10_000_000) throw new IllegalArgumentException("pointCount out of bounds"); } }
