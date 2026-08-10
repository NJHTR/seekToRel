package com.reportai.recovery;
public record DependencyRepairPlan(String planDigest, boolean precheckPassed, String lockfileDigest) implements RecoveryPayload { public DependencyRepairPlan { planDigest=PayloadRules.text(planDigest,"planDigest",128); lockfileDigest=PayloadRules.text(lockfileDigest,"lockfileDigest",128); } }
