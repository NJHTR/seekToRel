package com.reportai.recovery;
public record UserInputRequest(String promptCode, String promptDigest) implements RecoveryPayload { public UserInputRequest { promptCode=PayloadRules.text(promptCode,"promptCode",128); promptDigest=PayloadRules.text(promptDigest,"promptDigest",128); } }
