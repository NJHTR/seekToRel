CREATE TABLE IF NOT EXISTS workflow_definition (
    tenant_id       varchar(64) NOT NULL,
    definition_id   varchar(128) NOT NULL,
    version         integer NOT NULL,
    start_node_id   varchar(128) NOT NULL,
    nodes           jsonb NOT NULL,
    transitions     jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at      timestamptz(6) NOT NULL,
    CONSTRAINT pk_workflow_definition PRIMARY KEY (tenant_id, definition_id, version),
    CONSTRAINT fk_workflow_definition_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT ck_workflow_definition_version_positive CHECK (version > 0),
    CONSTRAINT ck_workflow_definition_nodes_object CHECK (jsonb_typeof(nodes) = 'object'),
    CONSTRAINT ck_workflow_definition_transitions_array CHECK (jsonb_typeof(transitions) = 'array'),
    CONSTRAINT ck_workflow_definition_id_not_blank CHECK (length(trim(definition_id)) > 0),
    CONSTRAINT ck_workflow_definition_start_node_not_blank CHECK (length(trim(start_node_id)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_workflow_definition_latest
    ON workflow_definition (tenant_id, definition_id, version DESC);
