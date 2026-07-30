CREATE TABLE conversation (
    id          VARCHAR(128) PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    task_id     VARCHAR(64),
    title       VARCHAR(256) NOT NULL DEFAULT '',
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ(6) NOT NULL,
    updated_at  TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT fk_conversation_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_conversation_task FOREIGN KEY (task_id) REFERENCES report_task (id),
    CONSTRAINT uq_conversation_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_conversation_version CHECK (version >= 0),
    CONSTRAINT ck_conversation_title CHECK (length(title) <= 256)
);

CREATE INDEX idx_conversation_tenant_updated
    ON conversation (tenant_id, updated_at DESC, id);

CREATE TABLE conversation_message (
    id              VARCHAR(128) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    role            VARCHAR(16) NOT NULL,
    content         TEXT NOT NULL,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT fk_conversation_message_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_conversation_message_conversation
        FOREIGN KEY (tenant_id, conversation_id) REFERENCES conversation (tenant_id, id),
    CONSTRAINT ck_conversation_message_role CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT', 'TOOL')),
    CONSTRAINT ck_conversation_message_content CHECK (length(trim(content)) > 0),
    CONSTRAINT ck_conversation_message_metadata CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX idx_conversation_message_history
    ON conversation_message (tenant_id, conversation_id, created_at DESC, id DESC);
