package com.reportai.review.precheck;

import com.reportai.common.contract.*;
import com.reportai.common.tenant.TenantId;
import java.time.Instant;
import java.util.*;

public record PrecheckRequest(TenantId tenantId, TaskId taskId, String operation, AgentId agentId,
                              InputSnapshot inputSnapshot, String input, String format, String schemaVersion,
                              List<PrecheckEvidence> evidence, RulePack rulePack, ContextBudget contextBudget,
                              List<ContextCandidate> context, Instant policyExpiresAt,
                              List<Sha256Digest> priorInputDigests) {
    public PrecheckRequest {
        tenantId = Objects.requireNonNull(tenantId, "tenantId"); taskId = Objects.requireNonNull(taskId, "taskId"); agentId = Objects.requireNonNull(agentId, "agentId"); inputSnapshot = Objects.requireNonNull(inputSnapshot, "inputSnapshot");
        if (!tenantId.equals(inputSnapshot.tenantId())) throw new IllegalArgumentException("input snapshot tenant mismatch");
        operation = text(operation, "operation", 128); input = Objects.requireNonNull(input, "input"); format = text(format, "format", 64); schemaVersion = text(schemaVersion, "schemaVersion", 32);
        evidence = evidence == null ? List.of() : List.copyOf(evidence); rulePack = Objects.requireNonNull(rulePack, "rulePack"); contextBudget = contextBudget == null ? ContextBudget.defaults() : contextBudget; context = context == null ? List.of() : List.copyOf(context); policyExpiresAt = Objects.requireNonNull(policyExpiresAt, "policyExpiresAt"); priorInputDigests = priorInputDigests == null ? List.of() : List.copyOf(priorInputDigests);
    }
    public PrecheckRequest(TenantId tenantId, TaskId taskId, String operation, AgentId agentId,
                           InputSnapshot inputSnapshot, String input, String format, String schemaVersion,
                           List<PrecheckEvidence> evidence, RulePack rulePack, ContextBudget contextBudget,
                           List<ContextCandidate> context, Instant policyExpiresAt) {
        this(tenantId, taskId, operation, agentId, inputSnapshot, input, format, schemaVersion, evidence,
                rulePack, contextBudget, context, policyExpiresAt, List.of());
    }
    private static String text(String v, String f, int m) { Objects.requireNonNull(v, f); var n=v.strip(); if(n.isBlank()||n.length()>m||n.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(f+" is invalid"); return n; }
}
