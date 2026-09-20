-- Development seed data is deliberately controlled by a Flyway placeholder.
-- Set DEV_SEED_ENABLED=false outside local development. Only BCrypt hashes are persisted.
SET @agentops_dev_seed_enabled = '${dev_seed_enabled}';

INSERT INTO iam_permission (id, code, name, description)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000101'),
       'admin:security-check',
       '管理员安全检查',
       '访问管理员权限验证接口'
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO iam_permission (id, code, name, description)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000102'),
       'identity:me:read',
       '查看当前用户',
       '读取当前登录用户信息'
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO iam_role (
    id, tenant_id, code, name, status, version,
    created_at, updated_at, created_by, updated_by
)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000201'),
       'default',
       'ADMIN',
       '系统管理员',
       'ACTIVE',
       0,
       UTC_TIMESTAMP(6),
       UTC_TIMESTAMP(6),
       'flyway-v2',
       'flyway-v2'
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO app_user (
    id, tenant_id, username, password_hash, display_name, email,
    team_id, status, version, created_at, updated_at, created_by, updated_by
)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000301'),
       'default',
       'admin',
       '${dev_admin_password_hash}',
       '开发环境管理员',
       'admin@agentops.local',
       NULL,
       'ACTIVE',
       0,
       UTC_TIMESTAMP(6),
       UTC_TIMESTAMP(6),
       'flyway-v2',
       'flyway-v2'
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000201'),
       permission_id,
       UTC_TIMESTAMP(6),
       'flyway-v2'
FROM (
    SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000101') AS permission_id
    UNION ALL
    SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000102')
) permissions
WHERE @agentops_dev_seed_enabled = 'true';

INSERT INTO user_role (user_id, role_id, created_at, created_by)
SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000301'),
       UUID_TO_BIN('00000000-0000-0000-0000-000000000201'),
       UTC_TIMESTAMP(6),
       'flyway-v2'
WHERE @agentops_dev_seed_enabled = 'true';
