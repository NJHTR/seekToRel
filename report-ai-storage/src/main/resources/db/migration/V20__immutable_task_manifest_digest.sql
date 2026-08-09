ALTER TABLE report_task
    ADD COLUMN IF NOT EXISTS manifest_digest varchar(64);

ALTER TABLE report_task
    ADD CONSTRAINT ck_report_task_manifest_digest
    CHECK (manifest_digest IS NULL OR manifest_digest ~ '^[0-9a-fA-F]{64}$');

CREATE OR REPLACE FUNCTION report_ai_reject_manifest_digest_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.manifest_digest IS NOT NULL
       AND NEW.manifest_digest IS DISTINCT FROM OLD.manifest_digest THEN
        RAISE EXCEPTION 'report task manifest_digest is immutable'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_report_task_manifest_digest_immutable ON report_task;
CREATE TRIGGER trg_report_task_manifest_digest_immutable
    BEFORE UPDATE OF manifest_digest ON report_task
    FOR EACH ROW
    EXECUTE FUNCTION report_ai_reject_manifest_digest_mutation();
