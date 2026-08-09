-- Account lifecycle hardening: verification/recovery credentials, role history and audit.
-- Raw email/password-reset tokens are never persisted; only SHA-256 digests are stored.

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ(6),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ(6),
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS lock_reason VARCHAR(512);

-- Accounts created by the pre-verification local flow are retained as verified during the
-- forward migration. New registration code must create PENDING_EMAIL and set this column only
-- after a successful one-time verification.
UPDATE user_account
   SET email_verified_at = created_at
 WHERE status = 'ACTIVE'
   AND email_verified_at IS NULL;

-- Preserve legacy locked accounts while introducing explicit lock metadata. The original schema
-- had no lock timestamp, so updated_at is the only trustworthy forward-migration value available.
UPDATE user_account
   SET locked_at = updated_at
 WHERE status = 'LOCKED'
   AND locked_at IS NULL;

ALTER TABLE user_account
    DROP CONSTRAINT IF EXISTS ck_user_account_status;

ALTER TABLE user_account
    ADD CONSTRAINT ck_user_account_status
        CHECK (status IN ('PENDING_EMAIL', 'ACTIVE', 'LOCKED', 'DISABLED')),
    ADD CONSTRAINT ck_user_account_version_non_negative CHECK (version >= 0),
    ADD CONSTRAINT ck_user_account_lock_metadata CHECK (
        (status = 'LOCKED' AND locked_at IS NOT NULL)
        OR (status <> 'LOCKED' AND locked_at IS NULL)
    ),
    ADD CONSTRAINT fk_user_account_locked_by FOREIGN KEY (locked_by)
        REFERENCES user_account (id);

-- A composite key lets every child table enforce that user_id and tenant_id belong together.
ALTER TABLE user_account
    ADD CONSTRAINT uq_user_account_id_tenant UNIQUE (id, tenant_id);

CREATE TABLE account_security_token (
    id              VARCHAR(64) PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    purpose         VARCHAR(32) NOT NULL,
    token_hash      CHAR(64) NOT NULL,
    expires_at      TIMESTAMPTZ(6) NOT NULL,
    consumed_at     TIMESTAMPTZ(6),
    revoked_at      TIMESTAMPTZ(6),
    created_at      TIMESTAMPTZ(6) NOT NULL,
    request_ip      VARCHAR(64) NOT NULL DEFAULT '',
    user_agent      VARCHAR(512) NOT NULL DEFAULT '',
    CONSTRAINT fk_account_security_token_user FOREIGN KEY (user_id, tenant_id)
        REFERENCES user_account (id, tenant_id),
    CONSTRAINT uq_account_security_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_account_security_token_purpose
        CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_account_security_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_account_security_token_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_account_security_token_consumed_at CHECK (
        consumed_at IS NULL OR consumed_at >= created_at
    ),
    CONSTRAINT ck_account_security_token_revoked_at CHECK (
        revoked_at IS NULL OR revoked_at >= created_at
    )
);

CREATE INDEX idx_account_security_token_user_purpose
    ON account_security_token (tenant_id, user_id, purpose, expires_at DESC);
CREATE INDEX idx_account_security_token_expiry
    ON account_security_token (expires_at);

CREATE TABLE account_role_assignment (
    id              VARCHAR(64) PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    role            VARCHAR(32) NOT NULL,
    granted_by      VARCHAR(64) NOT NULL,
    granted_at      TIMESTAMPTZ(6) NOT NULL,
    revoked_at      TIMESTAMPTZ(6),
    CONSTRAINT fk_account_role_user FOREIGN KEY (user_id, tenant_id)
        REFERENCES user_account (id, tenant_id),
    CONSTRAINT fk_account_role_granted_by FOREIGN KEY (granted_by)
        REFERENCES user_account (id),
    CONSTRAINT ck_account_role_assignment_role
        CHECK (role IN ('MEMBER', 'TENANT_ADMIN', 'PLATFORM_ADMIN')),
    CONSTRAINT ck_account_role_assignment_revoked_at CHECK (
        revoked_at IS NULL OR revoked_at >= granted_at
    )
);

CREATE UNIQUE INDEX uq_account_role_active
    ON account_role_assignment (tenant_id, user_id, role)
    WHERE revoked_at IS NULL;
CREATE INDEX idx_account_role_history
    ON account_role_assignment (tenant_id, user_id, granted_at DESC);

CREATE TABLE account_audit_event (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    actor_user_id   VARCHAR(64),
    target_user_id  VARCHAR(64) NOT NULL,
    action          VARCHAR(64) NOT NULL,
    role            VARCHAR(32),
    reason          VARCHAR(512) NOT NULL DEFAULT '',
    request_id      VARCHAR(128) NOT NULL DEFAULT '',
    source_ip       VARCHAR(64) NOT NULL DEFAULT '',
    user_agent      VARCHAR(512) NOT NULL DEFAULT '',
    occurred_at     TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT fk_account_audit_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_account_audit_actor FOREIGN KEY (actor_user_id) REFERENCES user_account (id),
    -- The target must belong to the audited tenant. An actor is allowed to be a platform
    -- administrator from another tenant, so actor_user_id intentionally remains an id-only FK.
    CONSTRAINT fk_account_audit_target FOREIGN KEY (target_user_id, tenant_id)
        REFERENCES user_account (id, tenant_id),
    CONSTRAINT ck_account_audit_action CHECK (action IN (
        'EMAIL_VERIFICATION_REQUESTED', 'EMAIL_VERIFIED',
        'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED',
        'ACCOUNT_LOCKED', 'ACCOUNT_UNLOCKED', 'ACCOUNT_DISABLED', 'ACCOUNT_ENABLED',
        'ROLE_GRANTED', 'ROLE_REVOKED'
    )),
    CONSTRAINT ck_account_audit_role CHECK (
        (action IN ('ROLE_GRANTED', 'ROLE_REVOKED') AND role IN ('MEMBER', 'TENANT_ADMIN', 'PLATFORM_ADMIN'))
        OR (action NOT IN ('ROLE_GRANTED', 'ROLE_REVOKED') AND role IS NULL)
    )
);

CREATE INDEX idx_account_audit_tenant_time
    ON account_audit_event (tenant_id, occurred_at DESC);
CREATE INDEX idx_account_audit_target_time
    ON account_audit_event (target_user_id, occurred_at DESC);
