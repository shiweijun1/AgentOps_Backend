-- Ticket permissions are stable application metadata and are installed in every environment.
INSERT INTO iam_permission (id, code, name, description) VALUES
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000111'), 'ticket:create', '创建工单', '创建本人名下工单'),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000112'), 'ticket:read:self', '查看本人工单', '查看本人创建的工单'),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000113'), 'ticket:read:team', '查看团队工单', '查看所属团队或分配给本人的工单'),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000114'), 'ticket:read:any', '查看全部工单', '查看租户内全部工单'),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000115'), 'ticket:assign', '分派工单', '分派或重新分派工单'),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000116'), 'ticket:transition', '流转工单', '执行工单状态转换');

SET @agentops_dev_seed_enabled = '${dev_seed_enabled}';

INSERT INTO team (
    id, tenant_id, name, status, version, created_at, updated_at, created_by, updated_by
)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000401'), 'default', '默认客服组', 'ACTIVE', 0,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway-v3', 'flyway-v3'
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO iam_role (
    id, tenant_id, code, name, status, version, created_at, updated_at, created_by, updated_by
)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000202'), 'default', 'CUSTOMER', '客户', 'ACTIVE', 0,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway-v3', 'flyway-v3'
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO iam_role (
    id, tenant_id, code, name, status, version, created_at, updated_at, created_by, updated_by
)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000203'), 'default', 'SUPPORT', '客服', 'ACTIVE', 0,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway-v3', 'flyway-v3'
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO app_user (
    id, tenant_id, username, password_hash, display_name, email, team_id, status, version,
    created_at, updated_at, created_by, updated_by
)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000302'), 'default', 'customer',
       '${dev_admin_password_hash}', '开发客户', 'customer@agentops.local', NULL, 'ACTIVE', 0,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway-v3', 'flyway-v3'
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO app_user (
    id, tenant_id, username, password_hash, display_name, email, team_id, status, version,
    created_at, updated_at, created_by, updated_by
)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000303'), 'default', 'support',
       '${dev_admin_password_hash}', '开发客服', 'support@agentops.local',
       UUID_TO_BIN('00000000-0000-0000-0000-000000000401'), 'ACTIVE', 0,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway-v3', 'flyway-v3'
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO user_role (user_id, role_id, created_at, created_by)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000302'),
       UUID_TO_BIN('00000000-0000-0000-0000-000000000202'), UTC_TIMESTAMP(6), 'flyway-v3'
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO user_role (user_id, role_id, created_at, created_by)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000303'),
       UUID_TO_BIN('00000000-0000-0000-0000-000000000203'), UTC_TIMESTAMP(6), 'flyway-v3'
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000201'), permissions.permission_id,
       UTC_TIMESTAMP(6), 'flyway-v3'
FROM (
    SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000111') permission_id
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000112')
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000113')
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000114')
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000115')
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000116')
) permissions
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000202'), permissions.permission_id,
       UTC_TIMESTAMP(6), 'flyway-v3'
FROM (
    SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000111') permission_id
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000112')
) permissions
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000203'), permissions.permission_id,
       UTC_TIMESTAMP(6), 'flyway-v3'
FROM (
    SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000113') permission_id
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000115')
    UNION ALL SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000116')
) permissions
WHERE @agentops_dev_seed_enabled = 'true';
