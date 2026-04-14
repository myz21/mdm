CREATE TABLE agent_command (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command_uuid VARCHAR(255) NOT NULL UNIQUE,
    device_identifier VARCHAR(255) NOT NULL,
    command_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    payload JSONB,
    result JSONB,
    error_message TEXT,
    request_time TIMESTAMP NOT NULL DEFAULT NOW(),
    response_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agent_command_device ON agent_command(device_identifier);
CREATE INDEX idx_agent_command_uuid ON agent_command(command_uuid);
CREATE INDEX idx_agent_command_status ON agent_command(status);
