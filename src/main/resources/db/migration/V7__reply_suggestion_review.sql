ALTER TABLE ai_suggestion
    ADD COLUMN edited_content TEXT NULL AFTER original_content,
    ADD COLUMN edited_by VARCHAR(64) NULL AFTER adopted_at,
    ADD COLUMN edited_at DATETIME(6) NULL AFTER edited_by,
    ADD COLUMN rejected_by VARCHAR(64) NULL AFTER edited_at,
    ADD COLUMN rejected_at DATETIME(6) NULL AFTER rejected_by,
    ADD COLUMN rejection_reason VARCHAR(500) NULL AFTER rejected_at;

ALTER TABLE knowledge_citation
    ADD COLUMN tenant_id VARCHAR(64) NULL AFTER id;

UPDATE knowledge_citation citation
JOIN ai_suggestion suggestion ON suggestion.id = citation.suggestion_id
SET citation.tenant_id = suggestion.tenant_id;

ALTER TABLE knowledge_citation
    MODIFY COLUMN tenant_id VARCHAR(64) NOT NULL,
    ADD KEY idx_citation_tenant_suggestion (tenant_id, suggestion_id);

INSERT INTO iam_permission (id, code, name, description) VALUES
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000141'), 'suggestion:read', '查看 AI 回复建议', '查看授权工单的建议及知识引用'),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000142'), 'suggestion:review', '审核 AI 回复建议', '编辑、采纳或拒绝授权工单的建议');

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, UTC_TIMESTAMP(6), 'flyway-v7'
FROM iam_role role JOIN iam_permission permission
    ON permission.code IN ('suggestion:read', 'suggestion:review')
WHERE role.tenant_id = 'default' AND role.code IN ('ADMIN', 'SUPPORT')
  AND '${dev_seed_enabled}' = 'true';
