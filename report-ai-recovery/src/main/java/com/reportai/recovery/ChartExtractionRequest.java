package com.reportai.recovery;
public record ChartExtractionRequest(String artifactDigest, String chartId) implements RecoveryPayload { public ChartExtractionRequest { artifactDigest=PayloadRules.text(artifactDigest,"artifactDigest",128); chartId=PayloadRules.text(chartId,"chartId",128); } }
