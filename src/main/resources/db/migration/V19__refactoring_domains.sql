ALTER TABLE app_group
    ADD created_by VARCHAR(255);

ALTER TABLE app_group
    ADD last_modified_by VARCHAR(255);

ALTER TABLE enterprise_app
    ADD created_by VARCHAR(255);

ALTER TABLE enterprise_app
    ADD last_modified_by VARCHAR(255);

ALTER TABLE system_setting
    ADD created_by VARCHAR(255);

ALTER TABLE system_setting
    ADD last_modified_by VARCHAR(255);

ALTER TABLE agent_presence_history
    DROP COLUMN disconnected_at;

ALTER TABLE agent_activity_log
    ALTER COLUMN activity_type TYPE VARCHAR(255) USING (activity_type::VARCHAR(255));

ALTER TABLE device_auth_history
    ALTER COLUMN agent_version TYPE VARCHAR(255) USING (agent_version::VARCHAR(255));

ALTER TABLE device_auth_history
    ALTER COLUMN auth_source TYPE VARCHAR(255) USING (auth_source::VARCHAR(255));

ALTER TABLE device_auth_history
    ALTER COLUMN event_type TYPE VARCHAR(255) USING (event_type::VARCHAR(255));

ALTER TABLE device_auth_history
    ALTER COLUMN ip_address TYPE VARCHAR(255) USING (ip_address::VARCHAR(255));

ALTER TABLE agent_location
    ALTER COLUMN source TYPE VARCHAR(255) USING (source::VARCHAR(255));

ALTER TABLE agent_activity_log
    ALTER COLUMN status TYPE VARCHAR(255) USING (status::VARCHAR(255));

ALTER TABLE app_catalog_assignment
    ALTER COLUMN target_type TYPE VARCHAR(255) USING (target_type::VARCHAR(255));