CREATE TABLE agent_activity_log (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id         UUID REFERENCES apple_device(id),
    device_identifier VARCHAR(255) NOT NULL,
    activity_type     VARCHAR(30)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    details           JSONB,
    session_id        VARCHAR(255),
    started_at        TIMESTAMP    NOT NULL DEFAULT now(),
    ended_at          TIMESTAMP,
    duration_seconds  BIGINT,
    initiated_by      VARCHAR(255),
    created_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_activity_log_device_id   ON agent_activity_log (device_id);
CREATE INDEX idx_agent_activity_log_created_at  ON agent_activity_log (created_at DESC);
CREATE INDEX idx_agent_activity_log_session_id  ON agent_activity_log (session_id);
