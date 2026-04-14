-- Agent telemetry snapshots from the iOS/Android agent
CREATE TABLE agent_telemetry (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id           UUID,
    device_identifier   VARCHAR(255) NOT NULL,
    device_created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    server_received_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Battery
    battery_level       INTEGER,
    battery_charging    BOOLEAN,
    battery_state       VARCHAR(20),
    low_power_mode      BOOLEAN,

    -- Storage
    storage_total_bytes     BIGINT,
    storage_free_bytes      BIGINT,
    storage_used_bytes      BIGINT,
    storage_usage_percent   INTEGER,

    -- Memory
    memory_total_bytes      BIGINT,
    memory_available_bytes  BIGINT,

    -- System
    system_uptime       INTEGER,
    cpu_cores           INTEGER,
    thermal_state       VARCHAR(20),
    brightness          INTEGER,
    os_version          VARCHAR(50),
    model_identifier    VARCHAR(50),
    device_model        VARCHAR(100),

    -- Network
    network_type        VARCHAR(20),
    ip_address          VARCHAR(45),
    is_expensive        BOOLEAN,
    is_constrained      BOOLEAN,
    vpn_active          BOOLEAN,
    carrier_name        VARCHAR(100),
    radio_technology    VARCHAR(20),

    -- Security
    jailbreak_detected  BOOLEAN,
    debugger_attached   BOOLEAN,

    -- Locale
    locale_language     VARCHAR(10),
    locale_region       VARCHAR(10),
    locale_timezone     VARCHAR(100),

    CONSTRAINT fk_telemetry_device FOREIGN KEY (device_id) REFERENCES apple_device (id)
);

CREATE INDEX idx_telemetry_device_id ON agent_telemetry (device_id);
CREATE INDEX idx_telemetry_device_identifier ON agent_telemetry (device_identifier);
CREATE INDEX idx_telemetry_device_created_at ON agent_telemetry (device_created_at);
CREATE INDEX idx_telemetry_server_received_at ON agent_telemetry (server_received_at);

-- Agent location data from the iOS/Android agent
CREATE TABLE agent_location (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id           UUID,
    device_identifier   VARCHAR(255) NOT NULL,
    device_created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    server_received_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    latitude            DOUBLE PRECISION NOT NULL,
    longitude           DOUBLE PRECISION NOT NULL,
    altitude            DOUBLE PRECISION,
    horizontal_accuracy DOUBLE PRECISION,
    vertical_accuracy   DOUBLE PRECISION,
    speed               DOUBLE PRECISION,
    course              DOUBLE PRECISION,
    floor_level         INTEGER,

    CONSTRAINT fk_location_device FOREIGN KEY (device_id) REFERENCES apple_device (id)
);

CREATE INDEX idx_location_device_id ON agent_location (device_id);
CREATE INDEX idx_location_device_identifier ON agent_location (device_identifier);
CREATE INDEX idx_location_device_created_at ON agent_location (device_created_at);
CREATE INDEX idx_location_coords ON agent_location (latitude, longitude);
