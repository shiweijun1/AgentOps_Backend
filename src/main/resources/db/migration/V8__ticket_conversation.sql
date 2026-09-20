-- V1 already provides the per-ticket request idempotency key and both source links.
-- A nullable UNIQUE key permits ordinary messages while preventing two messages per suggestion.
ALTER TABLE ticket_message
    ADD UNIQUE KEY uk_ticket_message_source_suggestion (source_suggestion_id);

INSERT INTO iam_permission (id, code, name, description) VALUES
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000151'), 'ticket:message:read', '查看工单会话', '按工单数据范围和消息可见性查看会话'),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000152'), 'ticket:message:reply', '发送工单公开回复', '客户本人或有权客服发送平台内公开消息'),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000153'), 'ticket:message:note', '添加内部备注', '客服在有权工单添加仅内部可见备注'),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000154'), 'ticket:suggestion:send', '发送已采纳建议', '将已采纳建议的最终快照发送到工单会话');

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, UTC_TIMESTAMP(6), 'flyway-v8'
FROM iam_role role JOIN iam_permission permission
    ON permission.code IN ('ticket:message:read', 'ticket:message:reply',
                           'ticket:message:note', 'ticket:suggestion:send')
WHERE role.tenant_id = 'default' AND role.code IN ('ADMIN', 'SUPPORT')
  AND '${dev_seed_enabled}' = 'true';

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, UTC_TIMESTAMP(6), 'flyway-v8'
FROM iam_role role JOIN iam_permission permission
    ON permission.code IN ('ticket:message:read', 'ticket:message:reply')
WHERE role.tenant_id = 'default' AND role.code = 'CUSTOMER'
  AND '${dev_seed_enabled}' = 'true';
