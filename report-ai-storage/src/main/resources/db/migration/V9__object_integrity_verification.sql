ALTER TABLE object_integrity
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ(6),
    ADD COLUMN IF NOT EXISTS verification_error VARCHAR(4096);

ALTER TABLE object_integrity
    ADD CONSTRAINT object_integrity_verification_status_ck
    CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED', 'FAILED'));

CREATE INDEX IF NOT EXISTS object_integrity_verification_idx
    ON object_integrity (verification_status, verified_at, created_at);
