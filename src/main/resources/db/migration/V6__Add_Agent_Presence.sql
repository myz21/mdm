-- Agent presence fields on apple_device
ALTER TABLE apple_device ADD COLUMN agent_online BOOLEAN DEFAULT false;
ALTER TABLE apple_device ADD COLUMN agent_last_seen_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE apple_device ADD COLUMN agent_version VARCHAR(50);
ALTER TABLE apple_device ADD COLUMN agent_platform VARCHAR(20);

-- Agent presence history — tracks each connection session (connect → disconnect)
CREATE TABLE agent_presence_history (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id         UUID,
    device_identifier VARCHAR(255) NOT NULL,
    connected_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    disconnected_at   TIMESTAMP WITHOUT TIME ZONE,
    duration_seconds  BIGINT,
    agent_version     VARCHAR(50),
    agent_platform    VARCHAR(20),
    disconnect_reason VARCHAR(100),
    created_at        TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_presence_history_device FOREIGN KEY (device_id) REFERENCES apple_device (id)
);

CREATE INDEX idx_presence_history_device_id ON agent_presence_history (device_id);
CREATE INDEX idx_presence_history_connected_at ON agent_presence_history (connected_at);
CREATE INDEX idx_presence_history_device_identifier ON agent_presence_history (device_identifier);
