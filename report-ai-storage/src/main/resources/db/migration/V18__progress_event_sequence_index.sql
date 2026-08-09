-- The durable event cursor is the database-assigned sequence. Keep the old timestamp index
-- available only through the migration history; keyset reads must use the authoritative key.
DROP INDEX IF EXISTS progress_event_task_order_idx;
CREATE INDEX progress_event_task_sequence_idx
    ON progress_event (tenant_id, task_id, event_sequence ASC);
