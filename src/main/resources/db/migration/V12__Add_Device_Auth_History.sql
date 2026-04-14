CREATE TABLE device_auth_history (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id         UUID REFERENCES apple_device(id),
    device_identifier VARCHAR(255) NOT NULL,
    identity_id       UUID REFERENCES apple_identity(id),
    username          VARCHAR(255),
    auth_source       VARCHAR(20) NOT NULL,  -- 'AGENT' | 'SETUP'
    event_type        VARCHAR(20) NOT NULL,  -- 'SIGN_IN' | 'SIGN_OUT'
    ip_address        VARCHAR(45),
    agent_version     VARCHAR(50),
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_dah_device ON device_auth_history(device_id);
CREATE INDEX idx_dah_identity ON device_auth_history(identity_id);
CREATE INDEX idx_dah_created ON device_auth_history(created_at DESC);
