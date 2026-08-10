package com.reportai.recovery;
public record CodeDiagnosisRequest(String codeDigest, String failureFingerprint) implements RecoveryPayload { public CodeDiagnosisRequest { codeDigest=PayloadRules.text(codeDigest,"codeDigest",128); failureFingerprint=PayloadRules.text(failureFingerprint,"failureFingerprint",128); } }
