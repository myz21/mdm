-- GIN trigram indexes for fuzzy search on app name and bundle identifier
CREATE INDEX IF NOT EXISTS idx_apple_device_apps_name_trgm
    ON apple_device_apps USING gin (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_apple_device_apps_bundle_id_trgm
    ON apple_device_apps USING gin (bundle_identifier gin_trgm_ops);

-- Composite B-tree index for efficient app-device lookups
CREATE INDEX IF NOT EXISTS idx_apple_device_apps_bundle_device
    ON apple_device_apps (bundle_identifier, device_id);
