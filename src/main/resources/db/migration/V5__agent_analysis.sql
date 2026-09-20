ALTER TABLE ai_analysis_result
    ADD COLUMN category_code VARCHAR(32) NOT NULL AFTER input_revision;

ALTER TABLE agent_run
    ADD COLUMN execution_count INT NOT NULL DEFAULT 0 AFTER attempt_no,
    ADD KEY idx_agent_run_claim (status, lease_until, created_at),
    ADD CONSTRAINT chk_agent_execution_count CHECK (execution_count >= 0);

INSERT INTO iam_permission (id, code, name, description) VALUES
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000121'), 'agent:read', '查看 Agent 分析', '查看有权访问的工单的 Agent 执行轨迹和分析结果'),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000122'), 'agent:rerun', '重新分析工单', '为有权访问的工单创建新一次分析');

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, UTC_TIMESTAMP(6), 'flyway-v5'
FROM iam_role role JOIN iam_permission permission
    ON permission.code IN ('agent:read', 'agent:rerun')
WHERE role.tenant_id = 'default' AND role.code IN ('ADMIN', 'SUPPORT')
  AND '${dev_seed_enabled}' = 'true';
