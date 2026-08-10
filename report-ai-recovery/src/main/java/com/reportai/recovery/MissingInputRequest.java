package com.reportai.recovery;
public record MissingInputRequest(String inputName, String expectedDigest) implements RecoveryPayload { public MissingInputRequest { inputName=PayloadRules.text(inputName,"inputName",128); expectedDigest=PayloadRules.text(expectedDigest,"expectedDigest",128); } }
