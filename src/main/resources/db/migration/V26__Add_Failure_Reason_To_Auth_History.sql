ALTER TABLE device_auth_history ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(200);
