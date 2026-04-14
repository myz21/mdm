CREATE TABLE enrollment_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID,
    udid VARCHAR(255),
    enrollment_id VARCHAR(128),
    enrollment_user_id VARCHAR(256),
    enrollment_type VARCHAR(50),
    is_user_enrollment BOOLEAN DEFAULT false,
    serial_number VARCHAR(255),
    product_name VARCHAR(255),
    os_version VARCHAR(50),
    build_version VARCHAR(50),
    token VARCHAR(2048),
    push_magic VARCHAR(255),
    status VARCHAR(50),
    enrolled_at TIMESTAMP,
    unenrolled_at TIMESTAMP,
    unenroll_reason VARCHAR(50),
    account_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_enrollment_history_device_id ON enrollment_history(device_id);
CREATE INDEX idx_enrollment_history_account_id ON enrollment_history(account_id);
