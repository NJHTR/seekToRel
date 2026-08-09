package com.reportai.agent;

import com.reportai.common.contract.InputSnapshot;
import com.reportai.common.time.TimeSource;
import com.reportai.review.precheck.PrecheckDecision;
import com.reportai.review.precheck.PrecheckResult;
import com.reportai.review.precheck.PrecheckRoute;
import com.reportai.review.precheck.RulePack;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Fail-closed validation of the receipt that authorizes one Agent invocation. */
public final class AgentPrecheckGuard {
    public record Validation(boolean allowed, String code, PrecheckRoute route) {
        public Validation { code = Objects.requireNonNull(code, "code"); route = Objects.requireNonNull(route, "route"); }
        public static Validation allow() { return new Validation(true, "OK", PrecheckRoute.AGENT); }
        public static Validation deny(String code, PrecheckRoute route) { return new Validation(false, code, route); }
    }

    private final TimeSource timeSource;
    private final Duration maxAge;
    private final RulePack expectedRulePack;

    public AgentPrecheckGuard() { this(TimeSource.systemUtc(), Duration.ofHours(1), null); }

    public AgentPrecheckGuard(TimeSource timeSource, Duration maxAge, RulePack expectedRulePack) {
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
        if (maxAge.isNegative() || maxAge.isZero()) throw new IllegalArgumentException("maxAge must be positive");
        this.expectedRulePack = expectedRulePack;
    }

    public Validation validate(AgentContext context, String agentType) {
        if (context == null || context.inputSnapshot() == null || context.tenantId() == null || context.taskId() == null || context.taskId().isBlank()) return Validation.deny("PRECHECK_MISSING", PrecheckRoute.REJECT);
        if (agentType == null || agentType.isBlank()) return Validation.deny("AGENT_ID_MISSING", PrecheckRoute.REJECT);
        PrecheckResult receipt = context.precheckReceipts().getOrDefault(agentType, context.precheckResult());
        if (receipt == null) return Validation.deny("PRECHECK_MISSING", PrecheckRoute.REJECT);
        InputSnapshot snapshot = context.inputSnapshot();
        if (!context.tenantId().equals(receipt.tenantId()) || !context.tenantId().equals(snapshot.tenantId()) || !receipt.tenantId().equals(receipt.inputSnapshot().tenantId())) return Validation.deny("PRECHECK_TENANT_MISMATCH", receipt.recommendedRoute());
        if (!context.taskId().equals(receipt.taskId().value())) return Validation.deny("PRECHECK_TASK_MISMATCH", receipt.recommendedRoute());
        if (!agentType.equals(receipt.agentId().value())) return Validation.deny("PRECHECK_AGENT_MISMATCH", receipt.recommendedRoute());
        if (!snapshot.snapshotId().equals(receipt.inputSnapshot().snapshotId()) || !snapshot.contentDigest().equals(receipt.inputSnapshot().contentDigest()) || !snapshot.contentDigest().equals(receipt.originalInputDigest())) return Validation.deny("PRECHECK_INPUT_MISMATCH", receipt.recommendedRoute());
        if (expectedRulePack != null && (!expectedRulePack.id().equals(receipt.rulePack().id()) || !expectedRulePack.version().equals(receipt.rulePack().version()) || !expectedRulePack.digest().equals(receipt.rulePack().digest()) || !expectedRulePack.policyVersion().equals(receipt.rulePack().policyVersion()))) return Validation.deny("PRECHECK_RULE_PACK_MISMATCH", receipt.recommendedRoute());
        Instant now = timeSource.now();
        if (receipt.createdAt().plus(maxAge).compareTo(now) <= 0) return Validation.deny("PRECHECK_STALE", receipt.recommendedRoute());
        if (receipt.decision() != PrecheckDecision.ACCEPT || receipt.recommendedRoute() != PrecheckRoute.AGENT || !receipt.acceptedForAgent()) return Validation.deny("PRECHECK_ROUTE_NOT_ALLOWED", receipt.recommendedRoute());
        return Validation.allow();
    }
}
