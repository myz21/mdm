-- Add source column to agent_location to distinguish between
-- AGENT (MQTT from iOS agent) and MDM_LOST_MODE (DeviceLocation command response)
ALTER TABLE agent_location ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'AGENT';
