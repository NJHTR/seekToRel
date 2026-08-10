package com.reportai.workflow.plan;

import com.reportai.common.tenant.TenantId;

import java.util.List;
import java.util.Optional;

/** Persistence port for immutable plan versions and restart-safe run snapshots. */
public interface PlanRepository {
    PlanDefinition create(PlanDefinition definition);

    default PlanDefinition create(PlanDefinition definition, String idempotencyKey) {
        return create(definition);
    }

    Optional<PlanDefinition> findDefinition(TenantId tenantId, String planId, long version);

    Optional<PlanDefinition> latestDefinition(TenantId tenantId, String planId);

    DefinitionWriteResult appendDefinition(PlanDefinition expected, PlanDefinition next,
                                           String idempotencyKey);

    default PlanDefinition save(PlanDefinition expected, PlanDefinition next, String idempotencyKey) {
        return appendDefinition(expected, next, idempotencyKey).definition();
    }

    default Optional<PlanDefinition> find(TenantId tenantId, String planId, long version) {
        return findDefinition(tenantId, planId, version);
    }

    PlanRun createRun(PlanRun run);

    Optional<PlanRun> findRun(TenantId tenantId, String runId);

    RunWriteResult appendRun(PlanRun expected, PlanRun next, PlanRunEvent event);

    default RunWriteResult appendRun(PlanRun expected, PlanRun next, String idempotencyKey) {
        PlanRunEvent event = PlanRunEventFactory.forTransition(expected, next, idempotencyKey);
        return appendRun(expected, next, event);
    }

    List<PlanRunEvent> runEvents(TenantId tenantId, String runId);

    PlanRun replayRun(TenantId tenantId, String runId);

    record DefinitionWriteResult(PlanDefinition definition, boolean duplicate) {
        public DefinitionWriteResult {
            if (definition == null) throw new NullPointerException("definition");
        }
    }

    record RunWriteResult(PlanRun run, PlanRunEvent event, boolean duplicate) {
        public RunWriteResult {
            if (run == null) throw new NullPointerException("run");
            if (event == null) throw new NullPointerException("event");
        }
    }
}
