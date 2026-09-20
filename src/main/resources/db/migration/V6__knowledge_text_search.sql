ALTER TABLE knowledge_chunk
    ADD COLUMN token_count_estimated BOOLEAN NOT NULL DEFAULT TRUE AFTER token_count,
    ADD FULLTEXT KEY ft_knowledge_chunk_content (content) WITH PARSER ngram;

INSERT INTO iam_permission (id, code, name, description) VALUES
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000131'), 'knowledge:manage', '管理知识库', '创建文章和版本、发布与撤回'),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000132'), 'knowledge:search', '检索知识库', '检索当前已发布且有效的知识片段');

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, UTC_TIMESTAMP(6), 'flyway-v6'
FROM iam_role role JOIN iam_permission permission
    ON permission.code IN ('knowledge:manage', 'knowledge:search')
WHERE role.tenant_id = 'default' AND role.code = 'ADMIN'
  AND '${dev_seed_enabled}' = 'true';

INSERT INTO role_permission (role_id, permission_id, created_at, created_by)
SELECT role.id, permission.id, UTC_TIMESTAMP(6), 'flyway-v6'
FROM iam_role role JOIN iam_permission permission
    ON permission.code = 'knowledge:search'
WHERE role.tenant_id = 'default' AND role.code = 'SUPPORT'
  AND '${dev_seed_enabled}' = 'true';
