package com.reportai.recovery;
public record ConflictResolution(String selectedDigest, String resolutionRef) implements RecoveryPayload { public ConflictResolution { selectedDigest=PayloadRules.text(selectedDigest,"selectedDigest",128); resolutionRef=PayloadRules.text(resolutionRef,"resolutionRef",128); } }
