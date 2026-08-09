-- Supports bounded age-based scans before physical orphan-object deletion.
CREATE INDEX IF NOT EXISTS object_integrity_orphan_retention_idx
    ON object_integrity (created_at, owner_type, tenant_id, owner_id, id);
