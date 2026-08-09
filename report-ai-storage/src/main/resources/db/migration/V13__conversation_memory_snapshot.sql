ALTER TABLE conversation_message
    ADD CONSTRAINT uq_conversation_message_tenant_conversation_id
        UNIQUE (tenant_id, conversation_id, id);

CREATE TABLE conversation_memory_snapshot (
    tenant_id          VARCHAR(64) NOT NULL,
    conversation_id    VARCHAR(128) NOT NULL,
    through_created_at TIMESTAMPTZ(6) NOT NULL,
    through_message_id VARCHAR(128) NOT NULL,
    summary            TEXT NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ(6) NOT NULL,
    updated_at         TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT pk_conversation_memory_snapshot PRIMARY KEY (tenant_id, conversation_id),
    CONSTRAINT fk_memory_snapshot_conversation
        FOREIGN KEY (tenant_id, conversation_id) REFERENCES conversation (tenant_id, id),
    CONSTRAINT fk_memory_snapshot_through_message
        FOREIGN KEY (tenant_id, conversation_id, through_message_id)
        REFERENCES conversation_message (tenant_id, conversation_id, id),
    CONSTRAINT ck_memory_snapshot_version CHECK (version >= 0),
    CONSTRAINT ck_memory_snapshot_summary CHECK (length(trim(summary)) > 0 AND length(summary) <= 131072)
);
