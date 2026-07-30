CREATE TABLE object_integrity (
    id          VARCHAR(128) PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    owner_type  VARCHAR(32) NOT NULL,
    owner_id    VARCHAR(128) NOT NULL,
    role        VARCHAR(32) NOT NULL,
    object_key  VARCHAR(512) NOT NULL,
    media_type  VARCHAR(255) NOT NULL,
    object_size BIGINT NOT NULL,
    sha256      CHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT fk_object_integrity_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT ck_object_integrity_size_positive CHECK (object_size > 0),
    CONSTRAINT ck_object_integrity_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT uq_object_integrity_owner_role UNIQUE (tenant_id, owner_type, owner_id, role)
);

CREATE INDEX object_integrity_owner_idx
    ON object_integrity (tenant_id, owner_type, owner_id, role);
