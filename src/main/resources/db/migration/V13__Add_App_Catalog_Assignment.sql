CREATE TABLE app_catalog_assignment (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_group_id UUID NOT NULL REFERENCES app_group(id) ON DELETE CASCADE,
    target_type  VARCHAR(20) NOT NULL,  -- 'ACCOUNT' | 'ACCOUNT_GROUP'
    target_id    UUID NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_cat_assign_unique ON app_catalog_assignment(app_group_id, target_type, target_id);
CREATE INDEX idx_cat_assign_target ON app_catalog_assignment(target_type, target_id);
