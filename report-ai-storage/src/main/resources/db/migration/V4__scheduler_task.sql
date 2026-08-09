CREATE TABLE scheduler_task (
    id VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    task_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    max_attempts INTEGER NOT NULL,
    initial_backoff_ms BIGINT NOT NULL,
    max_backoff_ms BIGINT NOT NULL,
    attempt INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    next_run_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(256),
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(4096),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT scheduler_task_status_ck CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','RETRY_WAITING','FAILED','CANCELLED')),
    CONSTRAINT scheduler_task_attempt_ck CHECK (attempt >= 0),
    CONSTRAINT scheduler_task_version_ck CHECK (version >= 0),
    CONSTRAINT scheduler_task_retry_ck CHECK (max_attempts BETWEEN 1 AND 100
        AND initial_backoff_ms >= 0 AND max_backoff_ms >= initial_backoff_ms),
    CONSTRAINT scheduler_task_idempotency_uk UNIQUE (tenant_id, task_type, idempotency_key)
);

CREATE INDEX scheduler_task_dispatch_idx ON scheduler_task (status, next_run_at, lease_until);
CREATE INDEX scheduler_task_tenant_idx ON scheduler_task (tenant_id, created_at DESC);
