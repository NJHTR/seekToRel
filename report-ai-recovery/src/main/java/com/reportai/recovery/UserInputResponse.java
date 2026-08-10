package com.reportai.recovery;
public record UserInputResponse(String responseDigest, String responseRef) implements RecoveryPayload { public UserInputResponse { responseDigest=PayloadRules.text(responseDigest,"responseDigest",128); responseRef=PayloadRules.text(responseRef,"responseRef",128); } }
