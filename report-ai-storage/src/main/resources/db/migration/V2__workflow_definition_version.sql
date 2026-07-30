ALTER TABLE workflow_instance
    ADD COLUMN IF NOT EXISTS definition_version integer NOT NULL DEFAULT 1;

ALTER TABLE workflow_instance
    ADD CONSTRAINT ck_workflow_definition_version_positive
    CHECK (definition_version > 0);
