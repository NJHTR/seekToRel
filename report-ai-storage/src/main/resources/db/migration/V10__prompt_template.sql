CREATE TABLE prompt_template (
    prompt_id   VARCHAR(128) NOT NULL,
    version     INTEGER NOT NULL,
    content     TEXT NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ(6) NOT NULL,
    updated_at  TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT pk_prompt_template PRIMARY KEY (prompt_id, version),
    CONSTRAINT ck_prompt_template_id CHECK (prompt_id ~ '^[A-Za-z][A-Za-z0-9_.-]{0,127}$'),
    CONSTRAINT ck_prompt_template_version CHECK (version > 0),
    CONSTRAINT ck_prompt_template_content CHECK (length(trim(content)) > 0)
);

CREATE UNIQUE INDEX uq_prompt_template_active
    ON prompt_template (prompt_id)
    WHERE active = TRUE;

CREATE INDEX idx_prompt_template_active
    ON prompt_template (prompt_id, active, version DESC);
