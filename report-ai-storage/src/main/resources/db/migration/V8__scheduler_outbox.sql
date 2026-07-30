CREATE TABLE scheduler_outbox (
    id                  VARCHAR(128) PRIMARY KEY,
    tenant_id           VARCHAR(128) NOT NULL,
    task_type           VARCHAR(128) NOT NULL,
    payload             TEXT NOT NULL,
    idempotency_key     VARCHAR(256) NOT NULL,
    max_attempts        INTEGER NOT NULL,
    initial_backoff_ms  BIGINT NOT NULL,
    max_backoff_ms      BIGINT NOT NULL,
    attempt             INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(32) NOT NULL,
    next_attempt_at     TIMESTAMPTZ NOT NULL,
    lease_owner         VARCHAR(256),
    lease_until         TIMESTAMPTZ,
    last_error          VARCHAR(4096),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_scheduler_outbox_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT scheduler_outbox_status_ck CHECK (status IN ('PENDING', 'IN_FLIGHT', 'PUBLISHED')),
    CONSTRAINT scheduler_outbox_attempt_ck CHECK (attempt >= 0),
    CONSTRAINT scheduler_outbox_retry_ck CHECK (
        max_attempts BETWEEN 1 AND 100
        AND initial_backoff_ms >= 0
        AND max_backoff_ms >= initial_backoff_ms
    ),
    CONSTRAINT scheduler_outbox_idempotency_uk UNIQUE (tenant_id, task_type, idempotency_key)
);

CREATE INDEX scheduler_outbox_dispatch_idx
    ON scheduler_outbox (status, next_attempt_at, lease_until, created_at);
