package com.reportai.recovery;
public record ResultConflict(String leftDigest, String rightDigest, String conflictType) implements RecoveryPayload { public ResultConflict { leftDigest=PayloadRules.text(leftDigest,"leftDigest",128); rightDigest=PayloadRules.text(rightDigest,"rightDigest",128); conflictType=PayloadRules.text(conflictType,"conflictType",128); } }
