package com.reportai.recovery;
public record CodeDiagnosis(boolean repairable, String diagnosisCode, String patchDigest) implements RecoveryPayload { public CodeDiagnosis { diagnosisCode=PayloadRules.text(diagnosisCode,"diagnosisCode",128); if(patchDigest!=null) patchDigest=PayloadRules.text(patchDigest,"patchDigest",128); } }
