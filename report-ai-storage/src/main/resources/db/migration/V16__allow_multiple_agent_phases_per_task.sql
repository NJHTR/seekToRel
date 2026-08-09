-- A report task owns several durable Agent phases (planner, coder/runtime,
-- document/reviewer). The pipeline_id is already the primary key, so a second
-- tenant+task uniqueness constraint incorrectly rejected the next phase.
DROP INDEX IF EXISTS uq_agent_pipeline_tenant_task;
