-- Apple Identity table (local copy of back_core Identity)
CREATE TABLE apple_identity (
    id                 UUID NOT NULL,
    creation_date      TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    username           VARCHAR(255) NOT NULL,
    email              VARCHAR(255),
    full_name          VARCHAR(255),
    source             VARCHAR(50) NOT NULL,
    external_id        VARCHAR(500),
    password_hash      VARCHAR(255),
    status             VARCHAR(255) DEFAULT 'ACTIVE',
    CONSTRAINT pk_apple_identity PRIMARY KEY (id)
);

-- Add identity_id FK to apple_account
ALTER TABLE apple_account ADD COLUMN identity_id UUID;
ALTER TABLE apple_account ADD CONSTRAINT FK_APPLE_ACCOUNT_ON_IDENTITY
    FOREIGN KEY (identity_id) REFERENCES apple_identity (id);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_apple_identity_email ON apple_identity(email);
CREATE INDEX IF NOT EXISTS idx_apple_identity_external_id ON apple_identity(external_id);
CREATE INDEX IF NOT EXISTS idx_apple_account_identity_id ON apple_account(identity_id);
