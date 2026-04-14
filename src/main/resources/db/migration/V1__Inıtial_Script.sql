CREATE TABLE abm_device
(
    id                   UUID         NOT NULL,
    creation_date        TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date   TIMESTAMP WITHOUT TIME ZONE,
    serial_number        VARCHAR(255) NOT NULL,
    model                VARCHAR(255),
    description          VARCHAR(255),
    color                VARCHAR(255),
    asset_tag            VARCHAR(255),
    os                   VARCHAR(255),
    device_family        VARCHAR(255),
    profile_id           UUID,
    profile_status       VARCHAR(255),
    profile_assign_time  VARCHAR(255),
    profile_push_time    VARCHAR(255),
    device_assigned_date VARCHAR(255),
    device_assigned_by   VARCHAR(255),
    CONSTRAINT pk_abm_device PRIMARY KEY (id)
);

CREATE TABLE abm_profile
(
    id                      UUID         NOT NULL,
    creation_date           TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date      TIMESTAMP WITHOUT TIME ZONE,
    profile_uuid            VARCHAR(255) NOT NULL,
    profile_name            VARCHAR(255),
    url                     VARCHAR(255),
    configuration_web_url   VARCHAR(255),
    allow_pairing           BOOLEAN,
    is_supervised           BOOLEAN,
    is_multi_user           BOOLEAN,
    is_mandatory            BOOLEAN,
    await_device_configured BOOLEAN,
    is_mdm_removable        BOOLEAN,
    auto_advance_setup      BOOLEAN,
    support_phone_number    VARCHAR(255),
    support_email_address   VARCHAR(255),
    org_magic               VARCHAR(255),
    department              VARCHAR(255),
    language                VARCHAR(255),
    region                  VARCHAR(255),
    skip_setup_items        JSONB,
    anchor_certs            JSONB,
    supervising_host_certs  JSONB,
    CONSTRAINT pk_abm_profile PRIMARY KEY (id)
);

CREATE TABLE app_group
(
    id                 UUID         NOT NULL,
    creation_date      TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    name               VARCHAR(255) NOT NULL,
    description        VARCHAR(1024),
    metadata           JSONB,
    CONSTRAINT pk_app_group PRIMARY KEY (id)
);

CREATE TABLE app_group_item
(
    id           UUID        NOT NULL,
    group_id     UUID        NOT NULL,
    app_type     VARCHAR(32) NOT NULL,
    track_id     VARCHAR(64),
    bundle_id    VARCHAR(512),
    artifact_ref VARCHAR(1024),
    display_name VARCHAR(512),
    order_idx    INTEGER,
    CONSTRAINT pk_app_group_item PRIMARY KEY (id)
);

CREATE TABLE app_supported_platforms
(
    app_id   UUID NOT NULL,
    platform VARCHAR(255)
);

CREATE TABLE apple_account
(
    id                 UUID         NOT NULL,
    created_by         VARCHAR(255),
    last_modified_by   VARCHAR(255),
    creation_date      TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    username           VARCHAR(255) NOT NULL,
    email              VARCHAR(255),
    managed_apple_id   VARCHAR(255),
    full_name          VARCHAR(255),
    status             VARCHAR(255),
    CONSTRAINT pk_apple_account PRIMARY KEY (id)
);

CREATE TABLE apple_account_devices
(
    account_id UUID NOT NULL,
    device_id  UUID NOT NULL,
    CONSTRAINT pk_apple_account_devices PRIMARY KEY (account_id, device_id)
);

CREATE TABLE apple_command
(
    id                 UUID NOT NULL,
    created_by         VARCHAR(255),
    last_modified_by   VARCHAR(255),
    creation_date      TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    template           TEXT,
    command_type       VARCHAR(255),
    command_uuid       VARCHAR(255),
    status             VARCHAR(255),
    request_time       TIMESTAMP WITHOUT TIME ZONE,
    execution_time     TIMESTAMP WITHOUT TIME ZONE,
    completion_time    TIMESTAMP WITHOUT TIME ZONE,
    failure_reason     VARCHAR(1024),
    apple_device_udid  VARCHAR(255),
    policy_id          UUID,
    CONSTRAINT pk_apple_command PRIMARY KEY (id)
);

CREATE TABLE apple_device
(
    id                                UUID    NOT NULL,
    creation_date                     TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date                TIMESTAMP WITHOUT TIME ZONE,
    build_version                     VARCHAR(255),
    os_version                        VARCHAR(255),
    serial_number                     VARCHAR(255),
    product_name                      VARCHAR(255),
    udid                              VARCHAR(255),
    enrollment_id                     VARCHAR(128),
    enrollment_user_id                VARCHAR(256),
    enrollment_type                   VARCHAR(32),
    is_user_enrollment                BOOLEAN,
    token                             VARCHAR(2048),
    push_magic                        VARCHAR(255),
    unlock_token                      VARCHAR(4096),
    is_declarative_management_enabled BOOLEAN,
    declarative_status                JSONB,
    declarative_token                 VARCHAR(248),
    applied_policy                    JSONB,
    is_compliant                      BOOLEAN NOT NULL,
    compliance_failures               JSONB,
    status                            VARCHAR(255),
    management_mode                   TEXT,
    CONSTRAINT pk_apple_device PRIMARY KEY (id)
);

CREATE TABLE apple_device_apps
(
    id                UUID         NOT NULL,
    bundle_size       INTEGER,
    bundle_identifier VARCHAR(255) NOT NULL,
    installing        BOOLEAN,
    name              VARCHAR(255),
    version           VARCHAR(255),
    short_version     VARCHAR(255),
    is_managed        BOOLEAN,
    has_configuration BOOLEAN,
    has_feedback      BOOLEAN,
    is_validated      BOOLEAN,
    management_flags  INTEGER,
    device_id         UUID         NOT NULL,
    CONSTRAINT pk_apple_device_apps PRIMARY KEY (id)
);

CREATE TABLE apple_device_information
(
    id                             UUID NOT NULL,
    creation_date                  TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date             TIMESTAMP WITHOUT TIME ZONE,
    app_analytics_enabled          BOOLEAN,
    awaiting_configuration         BOOLEAN,
    battery_level                  BYTEA,
    bluetoothmac                   VARCHAR(255),
    build_version                  VARCHAR(255),
    cellular_technology            INTEGER,
    data_roaming_enabled           BOOLEAN,
    device_capacity                BYTEA,
    device_name                    VARCHAR(255),
    diagnostic_submission_enabled  BOOLEAN,
    eas_device_identifier          VARCHAR(255),
    imei                           VARCHAR(255),
    activation_lock_enabled        BOOLEAN,
    cloud_backup_enabled           BOOLEAN,
    device_locator_service_enabled BOOLEAN,
    do_not_disturb_in_effect       BOOLEAN,
    mdmlost_mode_enabled           BOOLEAN,
    multi_user                     BOOLEAN,
    network_tethered               BOOLEAN,
    roaming                        BOOLEAN,
    supervised                     BOOLEAN,
    itunes_store_account_hash      JSONB,
    meid                           VARCHAR(255),
    model_name                     VARCHAR(255),
    modem_firmware_version         VARCHAR(255),
    os_version                     VARCHAR(255),
    personal_hotspot_enabled       BOOLEAN,
    product_name                   VARCHAR(255),
    service_subscriptions          JSONB,
    subscribermcc                  VARCHAR(255),
    subscribermnc                  VARCHAR(255),
    udid                           VARCHAR(255),
    voice_roaming_enabled          BOOLEAN,
    wifimac                        VARCHAR(255),
    itunes_store_account_is_active BOOLEAN,
    model                          TEXT,
    mdm_options                    JSONB,
    CONSTRAINT pk_apple_device_information PRIMARY KEY (id)
);

CREATE TABLE enrollment_audit_log
(
    id            UUID        NOT NULL,
    creation_date TIMESTAMP WITHOUT TIME ZONE,
    action        VARCHAR(64) NOT NULL,
    target_type   VARCHAR(64) NOT NULL,
    status        VARCHAR(32) NOT NULL,
    message       VARCHAR(1024),
    details       TEXT,
    error_message VARCHAR(1024),
    performed_by  VARCHAR(256),
    ip_address    VARCHAR(64),
    user_agent    VARCHAR(512),
    CONSTRAINT pk_enrollment_audit_log PRIMARY KEY (id)
);

CREATE TABLE enrollment_status
(
    id                             UUID NOT NULL,
    creation_date                  TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date             TIMESTAMP WITHOUT TIME ZONE,
    current_step                   INTEGER,
    completed_steps                INTEGER[],
    enrollment_completed           BOOLEAN,
    apns_cert_uploaded             BOOLEAN,
    apns_cert_subject              VARCHAR(512),
    apns_cert_issuer               VARCHAR(512),
    apns_cert_serial               VARCHAR(128),
    apns_cert_not_before           TIMESTAMP WITHOUT TIME ZONE,
    apns_cert_not_after            TIMESTAMP WITHOUT TIME ZONE,
    apns_push_topic                VARCHAR(256),
    dep_token_uploaded             BOOLEAN,
    dep_token_consumer_key         VARCHAR(256),
    dep_token_not_after            TIMESTAMP WITHOUT TIME ZONE,
    dep_org_name                   VARCHAR(256),
    dep_org_email                  VARCHAR(256),
    dep_org_phone                  VARCHAR(64),
    dep_org_address                VARCHAR(512),
    vpp_token_uploaded             BOOLEAN,
    vpp_token_not_after            TIMESTAMP WITHOUT TIME ZONE,
    vpp_org_name                   VARCHAR(256),
    vpp_location_name              VARCHAR(256),
    vendor_cert_not_after          TIMESTAMP WITHOUT TIME ZONE,
    vendor_cert_renewed_at         TIMESTAMP WITHOUT TIME ZONE,
    push_cert_renewed_after_vendor BOOLEAN,
    CONSTRAINT pk_enrollment_status PRIMARY KEY (id)
);

CREATE TABLE enterprise_app
(
    id                 UUID          NOT NULL,
    creation_date      TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    bundle_id          VARCHAR(512)  NOT NULL,
    version            VARCHAR(64),
    build_version      VARCHAR(64),
    display_name       VARCHAR(512),
    minimum_os_version VARCHAR(32),
    file_size_bytes    BIGINT,
    file_name          VARCHAR(512),
    storage_path       VARCHAR(1024) NOT NULL,
    file_hash          VARCHAR(128),
    platform           VARCHAR(32),
    icon_base64        TEXT,
    CONSTRAINT pk_enterprise_app PRIMARY KEY (id)
);

CREATE TABLE enterprise_app_supported_platforms
(
    enterprise_app_id UUID NOT NULL,
    platform          VARCHAR(255)
);

CREATE TABLE itunes_app_meta
(
    id                                      UUID    NOT NULL,
    creation_date                           TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date                      TIMESTAMP WITHOUT TIME ZONE,
    track_id                                BIGINT  NOT NULL,
    track_name                              VARCHAR(255),
    track_censored_name                     VARCHAR(255),
    bundle_id                               VARCHAR(255),
    version                                 VARCHAR(255),
    description                             VARCHAR(20480),
    release_notes                           VARCHAR(10240),
    formatted_price                         VARCHAR(255),
    price                                   DOUBLE PRECISION,
    currency                                VARCHAR(255),
    primary_genre_name                      VARCHAR(255),
    average_user_rating                     DOUBLE PRECISION,
    user_rating_count                       INTEGER,
    artwork_url512                          VARCHAR(1024),
    artwork_url100                          VARCHAR(1024),
    artwork_url60                           VARCHAR(1024),
    track_view_url                          VARCHAR(1024),
    seller_name                             VARCHAR(255),
    seller_url                              VARCHAR(1024),
    minimum_os_version                      VARCHAR(255),
    file_size_bytes                         VARCHAR(255),
    is_vpp_device_based_licensing_enabled   BOOLEAN,
    total_count                             INTEGER NOT NULL,
    assigned_count                          INTEGER NOT NULL,
    available_count                         INTEGER NOT NULL,
    retired_count                           INTEGER NOT NULL,
    is_game_center_enabled                  BOOLEAN,
    kind                                    VARCHAR(255),
    artist_view_url                         VARCHAR(1024),
    average_user_rating_for_current_version DOUBLE PRECISION,
    track_content_rating                    VARCHAR(255),
    content_advisory_rating                 VARCHAR(255),
    wrapper_type                            VARCHAR(255),
    pricing_param                           VARCHAR(255),
    CONSTRAINT pk_itunesappmeta PRIMARY KEY (id)
);

CREATE TABLE policy
(
    id                 UUID         NOT NULL,
    creation_date      TIMESTAMP WITHOUT TIME ZONE,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    name               VARCHAR(255) NOT NULL,
    platform           VARCHAR(255) NOT NULL,
    payload            JSONB,
    kiosk_lockdown     JSONB,
    status             VARCHAR(255),
    CONSTRAINT pk_policy PRIMARY KEY (id)
);

ALTER TABLE enterprise_app
    ADD CONSTRAINT uc_0942a05d70ae6c89a1f868c44 UNIQUE (bundle_id, version);

ALTER TABLE abm_device
    ADD CONSTRAINT uc_abm_device_serial_number UNIQUE (serial_number);

ALTER TABLE abm_profile
    ADD CONSTRAINT uc_abm_profile_profile_uuid UNIQUE (profile_uuid);

ALTER TABLE app_group
    ADD CONSTRAINT uc_app_group_name UNIQUE (name);

ALTER TABLE apple_device
    ADD CONSTRAINT uc_apple_device_udid UNIQUE (udid);

ALTER TABLE itunes_app_meta
    ADD CONSTRAINT uc_itunesappmeta_trackid UNIQUE (track_id);

ALTER TABLE abm_device
    ADD CONSTRAINT FK_ABM_DEVICE_ON_PROFILE FOREIGN KEY (profile_id) REFERENCES abm_profile (id);

ALTER TABLE apple_command
    ADD CONSTRAINT FK_APPLE_COMMAND_ON_APPLE_DEVICE_UDID FOREIGN KEY (apple_device_udid) REFERENCES apple_device (udid);

ALTER TABLE apple_command
    ADD CONSTRAINT FK_APPLE_COMMAND_ON_POLICY FOREIGN KEY (policy_id) REFERENCES policy (id);

ALTER TABLE apple_device_apps
    ADD CONSTRAINT FK_APPLE_DEVICE_APPS_ON_DEVICE FOREIGN KEY (device_id) REFERENCES apple_device (id);

ALTER TABLE apple_device_information
    ADD CONSTRAINT FK_APPLE_DEVICE_INFORMATION_ON_ID FOREIGN KEY (id) REFERENCES apple_device (id);

ALTER TABLE app_group_item
    ADD CONSTRAINT FK_APP_GROUP_ITEM_ON_GROUP FOREIGN KEY (group_id) REFERENCES app_group (id);

ALTER TABLE app_supported_platforms
    ADD CONSTRAINT fk_app_supported_platforms_on_itunes_app_meta FOREIGN KEY (app_id) REFERENCES itunes_app_meta (id);

ALTER TABLE apple_account_devices
    ADD CONSTRAINT fk_appaccdev_on_apple_account FOREIGN KEY (account_id) REFERENCES apple_account (id);

ALTER TABLE apple_account_devices
    ADD CONSTRAINT fk_appaccdev_on_apple_device FOREIGN KEY (device_id) REFERENCES apple_device (id);

ALTER TABLE enterprise_app_supported_platforms
    ADD CONSTRAINT fk_enterprise_app_supported_platforms_on_enterprise_app FOREIGN KEY (enterprise_app_id) REFERENCES enterprise_app (id);

-- ============================================
-- PostgreSQL Extension for Fuzzy Search
-- ============================================
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================
-- Trigram GIN Indexes (Fuzzy Search)
-- ============================================
CREATE INDEX IF NOT EXISTS idx_itunes_app_meta_track_name_trgm
    ON itunes_app_meta USING gin (track_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_itunes_app_meta_bundle_id_trgm
    ON itunes_app_meta USING gin (bundle_id gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_itunes_app_meta_seller_name_trgm
    ON itunes_app_meta USING gin (seller_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_itunes_app_meta_primary_genre_trgm
    ON itunes_app_meta USING gin (primary_genre_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_app_supported_platforms_trgm
    ON app_supported_platforms USING gin (platform gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_enterprise_app_display_name_trgm
    ON enterprise_app USING gin (display_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_enterprise_app_bundle_id_trgm
    ON enterprise_app USING gin (bundle_id gin_trgm_ops);

-- ============================================
-- Special Indexes
-- ============================================
-- Singleton pattern for enrollment_status (only one record allowed)
CREATE UNIQUE INDEX idx_enrollment_status_singleton ON enrollment_status ((TRUE));

-- ============================================
-- Performance Indexes
-- ============================================
CREATE INDEX IF NOT EXISTS idx_policy_platform ON policy(platform);
CREATE INDEX IF NOT EXISTS idx_apple_account_status ON apple_account(status);
CREATE INDEX IF NOT EXISTS idx_apple_account_managed_apple_id ON apple_account(managed_apple_id);
CREATE INDEX IF NOT EXISTS idx_abm_device_serial_number ON abm_device(serial_number);
CREATE INDEX IF NOT EXISTS idx_abm_device_profile_id ON abm_device(profile_id);
CREATE INDEX IF NOT EXISTS idx_abm_profile_profile_uuid ON abm_profile(profile_uuid);
CREATE INDEX IF NOT EXISTS idx_apple_device_enrollment_id ON apple_device(enrollment_id);
CREATE INDEX IF NOT EXISTS idx_apple_device_enrollment_type ON apple_device(enrollment_type);
CREATE INDEX IF NOT EXISTS idx_enrollment_audit_log_creation_date ON enrollment_audit_log (creation_date DESC);
CREATE INDEX IF NOT EXISTS idx_enrollment_audit_log_action ON enrollment_audit_log (action);
CREATE INDEX IF NOT EXISTS idx_enrollment_audit_log_target_type ON enrollment_audit_log (target_type);