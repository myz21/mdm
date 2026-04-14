CREATE TABLE bulk_command (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    command_type    VARCHAR(100) NOT NULL,
    total_devices   INTEGER NOT NULL DEFAULT 0,
    initiated_by    VARCHAR(255),
    payload         JSONB,
    created_at      TIMESTAMP WITHOUT TIME ZONE DEFAULT now(),
    CONSTRAINT pk_bulk_command PRIMARY KEY (id)
);

ALTER TABLE apple_command ADD COLUMN bulk_command_id UUID;
ALTER TABLE apple_command ADD CONSTRAINT fk_apple_command_bulk
    FOREIGN KEY (bulk_command_id) REFERENCES bulk_command(id);

CREATE INDEX idx_apple_command_bulk ON apple_command (bulk_command_id) WHERE bulk_command_id IS NOT NULL;
