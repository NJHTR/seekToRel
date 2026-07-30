CREATE TABLE workflow_event (
    id VARCHAR(128) PRIMARY KEY,
    workflow_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_workflow_event_instance FOREIGN KEY (workflow_id) REFERENCES workflow_instance (id),
    CONSTRAINT uq_workflow_event_version UNIQUE (workflow_id, version)
);

CREATE INDEX workflow_event_workflow_version_idx ON workflow_event (workflow_id, version);
