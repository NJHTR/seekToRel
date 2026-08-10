package com.reportai.recovery;
public record OcrRequest(String artifactDigest, String pageRange) implements RecoveryPayload { public OcrRequest { artifactDigest=PayloadRules.text(artifactDigest,"artifactDigest",128); pageRange=PayloadRules.text(pageRange,"pageRange",64); } }
