CREATE TABLE apple_device_location
(
    id                  VARCHAR(255) NOT NULL,
    device_id           UUID         NOT NULL,
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    altitude            DOUBLE PRECISION,
    speed               DOUBLE PRECISION,
    course              DOUBLE PRECISION,
    horizontal_accuracy DOUBLE PRECISION,
    vertical_accuracy   DOUBLE PRECISION,
    timestamp           TIMESTAMP WITHOUT TIME ZONE,
    created_date        TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_apple_device_location PRIMARY KEY (id)
);

ALTER TABLE apple_device_information
    ADD certificate_list JSONB;

ALTER TABLE apple_device_information
    ADD security_info JSONB;

ALTER TABLE apple_device_location
    ADD CONSTRAINT FK_APPLE_DEVICE_LOCATION_ON_DEVICE FOREIGN KEY (device_id) REFERENCES apple_device (id);