CREATE TABLE IF NOT EXISTS agent_pipeline_checkpoint (
    pipeline_id      varchar(128) PRIMARY KEY,
    tenant_id        varchar(64) NOT NULL,
    task_id          varchar(64) NOT NULL,
    agent_types      jsonb NOT NULL,
    next_agent_index integer NOT NULL DEFAULT 0,
    status           varchar(32) NOT NULL,
    state            jsonb NOT NULL DEFAULT '{}'::jsonb,
    message          varchar(16384) NOT NULL DEFAULT '',
    version          bigint NOT NULL DEFAULT 0,
    updated_at       timestamptz(6) NOT NULL,
    CONSTRAINT fk_agent_pipeline_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_agent_pipeline_task FOREIGN KEY (task_id) REFERENCES report_task (id),
    CONSTRAINT ck_agent_pipeline_agent_types_array CHECK (jsonb_typeof(agent_types) = 'array'),
    CONSTRAINT ck_agent_pipeline_state_object CHECK (jsonb_typeof(state) = 'object'),
    CONSTRAINT ck_agent_pipeline_next_agent_index_non_negative CHECK (next_agent_index >= 0),
    CONSTRAINT ck_agent_pipeline_version_non_negative CHECK (version >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_agent_pipeline_tenant_task
    ON agent_pipeline_checkpoint (tenant_id, task_id);
CREATE INDEX IF NOT EXISTS idx_agent_pipeline_tenant_status
    ON agent_pipeline_checkpoint (tenant_id, status, updated_at DESC);
