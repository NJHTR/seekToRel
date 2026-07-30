CREATE TABLE IF NOT EXISTS tenant (
    id              varchar(64) PRIMARY KEY,
    name            varchar(128) NOT NULL,
    status          varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz(6) NOT NULL,
    updated_at      timestamptz(6) NOT NULL,
    CONSTRAINT ck_tenant_name_not_blank CHECK (length(trim(name)) > 0)
);

CREATE TABLE IF NOT EXISTS document (
    id              varchar(64) PRIMARY KEY,
    tenant_id       varchar(64) NOT NULL,
    source_object_key varchar(512) NOT NULL,
    media_type      varchar(128) NOT NULL,
    status          varchar(32) NOT NULL DEFAULT 'UPLOADED',
    content         jsonb,
    metadata        jsonb NOT NULL DEFAULT '{}'::jsonb,
    version         bigint NOT NULL DEFAULT 0,
    created_at      timestamptz(6) NOT NULL,
    updated_at      timestamptz(6) NOT NULL,
    CONSTRAINT fk_document_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT ck_document_key_not_blank CHECK (length(trim(source_object_key)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_document_tenant_created
    ON document (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_document_tenant_status
    ON document (tenant_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS report_task (
    id              varchar(64) PRIMARY KEY,
    tenant_id       varchar(64) NOT NULL,
    document_object_key varchar(512) NOT NULL,
    status          varchar(32) NOT NULL,
    version         bigint NOT NULL DEFAULT 0,
    created_at      timestamptz(6) NOT NULL,
    updated_at      timestamptz(6) NOT NULL,
    CONSTRAINT fk_report_task_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT ck_report_task_key_not_blank CHECK (length(trim(document_object_key)) > 0),
    CONSTRAINT ck_report_task_version_non_negative CHECK (version >= 0)
);

CREATE INDEX IF NOT EXISTS idx_report_task_tenant_created
    ON report_task (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_report_task_tenant_status
    ON report_task (tenant_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS idempotency_record (
    tenant_id       varchar(64) NOT NULL,
    operation       varchar(128) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash    varchar(128) NOT NULL,
    resource_id     varchar(64) NOT NULL,
    status          varchar(32) NOT NULL,
    expires_at      timestamptz(6) NOT NULL,
    created_at      timestamptz(6) NOT NULL,
    updated_at      timestamptz(6) NOT NULL,
    PRIMARY KEY (tenant_id, operation, idempotency_key),
    CONSTRAINT fk_idempotency_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_expiry
    ON idempotency_record (expires_at);

CREATE TABLE IF NOT EXISTS workflow_instance (
    id              varchar(64) PRIMARY KEY,
    tenant_id       varchar(64) NOT NULL,
    task_id         varchar(64) NOT NULL,
    definition_id   varchar(128) NOT NULL,
    current_node    varchar(128),
    status          varchar(32) NOT NULL,
    state           jsonb NOT NULL DEFAULT '{}'::jsonb,
    version         bigint NOT NULL DEFAULT 0,
    created_at      timestamptz(6) NOT NULL,
    updated_at      timestamptz(6) NOT NULL,
    CONSTRAINT fk_workflow_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_workflow_task FOREIGN KEY (task_id) REFERENCES report_task (id),
    CONSTRAINT uq_workflow_tenant_task UNIQUE (tenant_id, task_id)
);

CREATE TABLE IF NOT EXISTS execution (
    id              varchar(64) PRIMARY KEY,
    tenant_id       varchar(64) NOT NULL,
    task_id         varchar(64) NOT NULL,
    language        varchar(32) NOT NULL,
    status          varchar(32) NOT NULL,
    exit_code       integer,
    stdout_object_key varchar(512),
    stderr_object_key varchar(512),
    policy          jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz(6) NOT NULL,
    updated_at      timestamptz(6) NOT NULL,
    CONSTRAINT fk_execution_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_execution_task FOREIGN KEY (task_id) REFERENCES report_task (id)
);

CREATE INDEX IF NOT EXISTS idx_execution_tenant_task
    ON execution (tenant_id, task_id, created_at DESC);

CREATE TABLE IF NOT EXISTS tool_log (
    id              varchar(64) PRIMARY KEY,
    tenant_id       varchar(64) NOT NULL,
    task_id         varchar(64) NOT NULL,
    tool_name       varchar(128) NOT NULL,
    status          varchar(32) NOT NULL,
    arguments       jsonb NOT NULL DEFAULT '{}'::jsonb,
    result_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    duration_ms     bigint,
    created_at      timestamptz(6) NOT NULL,
    CONSTRAINT fk_tool_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_tool_log_task FOREIGN KEY (task_id) REFERENCES report_task (id)
);

CREATE TABLE IF NOT EXISTS output_artifact (
    id              varchar(64) PRIMARY KEY,
    tenant_id       varchar(64) NOT NULL,
    task_id         varchar(64) NOT NULL,
    artifact_type   varchar(32) NOT NULL,
    object_key      varchar(512) NOT NULL,
    media_type      varchar(128),
    checksum        varchar(128),
    created_at      timestamptz(6) NOT NULL,
    CONSTRAINT fk_output_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_output_task FOREIGN KEY (task_id) REFERENCES report_task (id)
);

CREATE TABLE IF NOT EXISTS review_finding (
    id              varchar(64) PRIMARY KEY,
    tenant_id       varchar(64) NOT NULL,
    task_id         varchar(64) NOT NULL,
    severity        varchar(32) NOT NULL,
    rule_id         varchar(128) NOT NULL,
    message         varchar(2000) NOT NULL,
    location        varchar(512),
    created_at      timestamptz(6) NOT NULL,
    CONSTRAINT fk_review_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_review_task FOREIGN KEY (task_id) REFERENCES report_task (id)
);

CREATE INDEX IF NOT EXISTS idx_review_tenant_task
    ON review_finding (tenant_id, task_id, severity);
