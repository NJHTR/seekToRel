-- Append-only, tenant-scoped history for scheduler progress events.
CREATE TABLE progress_event (
    event_sequence BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    tenant_id   VARCHAR(128) NOT NULL,
    event_id    VARCHAR(128) NOT NULL,
    task_id     VARCHAR(128) NOT NULL,
    attempt     INTEGER NOT NULL,
    event_type  VARCHAR(32) NOT NULL,
    percent     INTEGER NOT NULL,
    message     VARCHAR(4096) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    attributes  JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT progress_event_pk PRIMARY KEY (tenant_id, event_id),
    CONSTRAINT progress_event_sequence_uk UNIQUE (event_sequence),
    CONSTRAINT progress_event_attempt_ck CHECK (attempt >= 0),
    CONSTRAINT progress_event_percent_ck CHECK (percent BETWEEN 0 AND 100),
    CONSTRAINT progress_event_message_ck CHECK (length(message) <= 4096),
    CONSTRAINT progress_event_type_ck CHECK (
        event_type IN ('QUEUED', 'STARTED', 'PROGRESS', 'RETRY_SCHEDULED', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    )
);

-- The scheduler task identity is tenant-scoped at the application boundary. Add the
-- matching unique key so history cannot be attached to another tenant's task id.
ALTER TABLE scheduler_task
    ADD CONSTRAINT scheduler_task_tenant_id_uk UNIQUE (tenant_id, id);

ALTER TABLE progress_event
    ADD CONSTRAINT fk_progress_event_scheduler_task
    FOREIGN KEY (tenant_id, task_id) REFERENCES scheduler_task (tenant_id, id);

CREATE INDEX progress_event_task_order_idx
    ON progress_event (tenant_id, task_id, occurred_at ASC, event_id ASC);
