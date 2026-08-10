package com.reportai.recovery;
public record DownloadDecision(boolean approved, String decisionRef) implements RecoveryPayload { public DownloadDecision { decisionRef=PayloadRules.text(decisionRef,"decisionRef",128); } }
