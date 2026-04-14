-- Convert presence history from session model to event log model.
-- Each row now represents a single state transition (ONLINE or OFFLINE).
ALTER TABLE agent_presence_history ADD COLUMN event_type VARCHAR(10);

-- Mark existing rows: rows with disconnected_at are OFFLINE events, otherwise ONLINE
UPDATE agent_presence_history SET event_type = 'OFFLINE' WHERE disconnected_at IS NOT NULL;
UPDATE agent_presence_history SET event_type = 'ONLINE' WHERE disconnected_at IS NULL;

ALTER TABLE agent_presence_history ALTER COLUMN event_type SET NOT NULL;

CREATE INDEX idx_presence_history_event_type ON agent_presence_history (event_type);
