package com.reportai.review.precheck;

import com.reportai.common.contract.*;
import com.reportai.common.tenant.TenantId;
import java.time.Instant;
import java.util.*;

public record PrecheckResult(int schemaVersion, String precheckId, TenantId tenantId, TaskId taskId, String operation,
                             AgentId agentId, InputSnapshot inputSnapshot, Sha256Digest originalInputDigest,
                             Sha256Digest normalizedInputDigest, RulePack rulePack, List<PrecheckFinding> findings,
                             EvidenceResolution evidenceResolution, RiskAssessment riskAssessment,
                             ContextProjection contextProjection, PrecheckRoute recommendedRoute,
                             PrecheckDecision decision, Instant createdAt) {
    public PrecheckResult { if(schemaVersion<=0) throw new IllegalArgumentException("schemaVersion must be positive"); precheckId=text(precheckId,"precheckId",128); tenantId=Objects.requireNonNull(tenantId); taskId=Objects.requireNonNull(taskId); operation=text(operation,"operation",128); agentId=Objects.requireNonNull(agentId); inputSnapshot=Objects.requireNonNull(inputSnapshot); originalInputDigest=Objects.requireNonNull(originalInputDigest); normalizedInputDigest=Objects.requireNonNull(normalizedInputDigest); rulePack=Objects.requireNonNull(rulePack); findings=findings==null?List.of():List.copyOf(findings); evidenceResolution=Objects.requireNonNull(evidenceResolution); riskAssessment=Objects.requireNonNull(riskAssessment); contextProjection=Objects.requireNonNull(contextProjection); recommendedRoute=Objects.requireNonNull(recommendedRoute); decision=Objects.requireNonNull(decision); createdAt=Objects.requireNonNull(createdAt); }
    public boolean acceptedForAgent() { return decision == PrecheckDecision.ACCEPT && recommendedRoute == PrecheckRoute.AGENT; }
    private static String text(String v,String f,int m){Objects.requireNonNull(v,f);var n=v.strip();if(n.isBlank()||n.length()>m||n.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException(f+" is invalid");return n;}
}
