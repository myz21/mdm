CREATE TABLE system_setting (
    id                  UUID NOT NULL,
    operation_identifier VARCHAR(128) NOT NULL,
    value               JSONB NOT NULL,
    creation_date       TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date  TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_system_setting PRIMARY KEY (id),
    CONSTRAINT uq_system_setting_identifier UNIQUE (operation_identifier)
);

CREATE INDEX idx_system_setting_identifier ON system_setting(operation_identifier);
