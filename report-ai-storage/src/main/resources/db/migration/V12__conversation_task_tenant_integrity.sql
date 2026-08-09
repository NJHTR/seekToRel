-- Preserve the original V11 checksum and tighten task ownership in a forward migration.
ALTER TABLE report_task
    ADD CONSTRAINT uq_report_task_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE conversation
    DROP CONSTRAINT fk_conversation_task;

ALTER TABLE conversation
    ADD CONSTRAINT fk_conversation_task FOREIGN KEY (tenant_id, task_id)
        REFERENCES report_task (tenant_id, id);
