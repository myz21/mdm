-- Report query optimization indexes

-- agent_presence_history: heatmap + device uptime
CREATE INDEX IF NOT EXISTS idx_presence_event_type_time ON agent_presence_history (event_type, connected_at);
CREATE INDEX IF NOT EXISTS idx_presence_device_time ON agent_presence_history (device_identifier, connected_at);

-- agent_location: time + spatial composite
CREATE INDEX IF NOT EXISTS idx_location_time_coords ON agent_location (device_created_at DESC, latitude, longitude);

-- apple_command: user stats + bulk command aggregations
CREATE INDEX IF NOT EXISTS idx_command_created_by_time ON apple_command (created_by, request_time DESC) WHERE created_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_command_bulk_status ON apple_command (bulk_command_id, status, completion_time) WHERE bulk_command_id IS NOT NULL;
