CREATE INDEX IF NOT EXISTS idx_apple_command_status ON apple_command (status);
CREATE INDEX IF NOT EXISTS idx_apple_command_type ON apple_command (command_type);
CREATE INDEX IF NOT EXISTS idx_apple_command_request_time ON apple_command (request_time DESC);
CREATE INDEX IF NOT EXISTS idx_apple_command_device_udid ON apple_command (apple_device_udid);
CREATE INDEX IF NOT EXISTS idx_apple_command_policy_id ON apple_command (policy_id);
CREATE INDEX IF NOT EXISTS idx_apple_command_composite
    ON apple_command (status, command_type, request_time DESC);
