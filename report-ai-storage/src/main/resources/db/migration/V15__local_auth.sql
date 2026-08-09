CREATE TABLE user_account (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    email           VARCHAR(320) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(128) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ(6) NOT NULL,
    updated_at      TIMESTAMPTZ(6) NOT NULL,
    last_login_at   TIMESTAMPTZ(6),
    CONSTRAINT fk_user_account_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT ck_user_account_email_not_blank CHECK (length(trim(email)) > 3),
    CONSTRAINT ck_user_account_password_hash_not_blank CHECK (length(trim(password_hash)) > 0),
    CONSTRAINT ck_user_account_display_name_not_blank CHECK (length(trim(display_name)) > 0),
    CONSTRAINT ck_user_account_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE UNIQUE INDEX uq_user_account_email_ci ON user_account (lower(email));
CREATE INDEX idx_user_account_tenant_created ON user_account (tenant_id, created_at DESC);

CREATE TABLE refresh_token (
    id              VARCHAR(64) PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    token_hash      CHAR(64) NOT NULL,
    expires_at      TIMESTAMPTZ(6) NOT NULL,
    revoked_at      TIMESTAMPTZ(6),
    created_at      TIMESTAMPTZ(6) NOT NULL,
    user_agent      VARCHAR(512) NOT NULL DEFAULT '',
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_refresh_token_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_refresh_token_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_refresh_token_user_active ON refresh_token (user_id, revoked_at, expires_at);
CREATE INDEX idx_refresh_token_expiry ON refresh_token (expires_at);
