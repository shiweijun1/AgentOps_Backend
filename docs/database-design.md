# AgentOps MySQL 设计

## 设计原则

- MySQL 是工单、权限、知识内容和 Agent 轨迹的权威数据源。
- 主键统一使用 `BINARY(16)` UUID；对外展示使用独立业务编号，例如 `ticket_no`。
- 所有业务时间使用 `DATETIME(6)`，应用与数据库统一按 UTC 保存。
- 当前状态保存在聚合根，分派和状态流转历史使用只追加记录。
- AI 原始建议不可覆盖，实际发送内容保存在 `ticket_message`。
- Redis 只承担缓存、限流和短期状态，不保存权威业务事实。
- RabbitMQ 至少一次投递，通过 Inbox/Outbox 和业务唯一键实现幂等效果。

## 表分组

| 模块 | 表 |
|---|---|
| 身份权限 | `team`、`app_user`、`iam_role`、`iam_permission`、`user_role`、`role_permission` |
| 工单 | `ticket_category`、`ticket`、`ticket_message`、`ticket_attachment`、`ticket_assignment_record`、`ticket_transition_record` |
| Agent | `agent_run`、`agent_step`、`ai_analysis_result`、`ai_suggestion`、`knowledge_citation` |
| 知识库 | `knowledge_article`、`knowledge_version`、`knowledge_chunk` |
| 审计与可靠性 | `operation_audit_log`、`idempotency_record`、`inbox_message`、`outbox_event` |

## 关键一致性约束

### 重复提交

`idempotency_record` 使用以下唯一键阻止同一技术请求重复创建资源：

```text
(tenant_id, requester_id, operation_type, idempotency_key)
```

`ticket.content_fingerprint` 只用于查找业务上的相似提交，不设置唯一约束，避免误伤确实需要重复提交的问题。

### 并发分派

当前团队和负责人保存在 `ticket`，并通过 `version` 执行乐观锁或条件更新。分派历史写入 `ticket_assignment_record`，两者必须在同一本地事务中提交。

### 重复消费

`inbox_message` 使用以下唯一键保证同一消费者只应用一次事件：

```text
(event_id, consumer_name)
```

`agent_run` 额外使用以下唯一键防止重复创建同一次 AI 尝试：

```text
(ticket_id, run_type, input_revision, attempt_no)
```

### 人工修改 AI 建议

`ai_suggestion.original_content` 保存不可变原文，`final_content_snapshot` 保存采纳时快照，正式发送内容保存到 `ticket_message`。`ai_suggestion.version` 防止同一建议被并发采纳和拒绝。

### 关闭后重新打开

`ticket.reopen_count` 保存当前累计次数，所有关闭和重新打开过程写入 `ticket_transition_record`。历史消息、AI Run 和建议均不删除。

## 索引策略

- 工单列表以 `(tenant_id, status, team_id, priority, created_at)` 支持人工队列。
- 个人工作台以 `(tenant_id, assignee_id, status)` 查询。
- 用户工单以 `(tenant_id, requester_id, created_at)` 查询。
- Agent 待执行任务以 `(status, lease_until, created_at)` 抢占；租户内轨迹查询仍使用 `(tenant_id, status, created_at)`。
- Outbox 调度以 `(status, next_retry_at, locked_until, created_at)` 查询。
- 审计以目标对象或操作人加时间倒序查询。

完整 DDL 位于 `src/main/resources/db/migration/V1__baseline_schema.sql`，由 Flyway 管理，生产环境禁止通过 Hibernate 自动修改表结构。

## 身份认证初始化

`V2__initialize_development_security_data.sql` 初始化开发环境管理员、`ADMIN` 角色及权限。迁移通过 `dev_seed_enabled` Flyway placeholder 控制：本地 Profile 默认启用，其他环境默认关闭。密码列只保存 BCrypt 哈希。

## 工单领域实现

`V3__initialize_ticket_security_and_demo_data.sql` 注册六个稳定的工单权限编码，并在本地开发开关启用时创建 CUSTOMER、SUPPORT、默认客服组及演示用户，同时为 V2 的 ADMIN 补充全部工单权限。

本阶段直接复用 V1 中的 `ticket`、`ticket_assignment_record`、`ticket_transition_record` 和 `idempotency_record`：

- `ticket.version` 映射为 JPA `@Version`，同时 API 命令携带 `expectedVersion`。
- 创建工单先通过 `idempotency_record` 的唯一键原子抢占请求，再在同一事务中写入工单和初始状态历史。
- 分派更新与分派历史、状态更新与状态历史分别在同一本地事务中提交。
- 查询始终包含 `tenant_id`，并根据本人、团队/负责人或租户全量权限追加数据范围谓词。
- V3 不修改 V1/V2 已发布迁移，也不通过 Hibernate 创建或修改表。

## Transactional Outbox 与 Inbox

`V4__add_outbox_publisher_leases.sql` 只扩展 V1 的 `outbox_event`，增加：

- `locked_by`：当前发布器实例标识。
- `locked_until`：发布租约到期时间，实例崩溃后可重新抢占。
- `last_error`：最近一次发布失败摘要，不保存完整消息正文。
- `idx_outbox_claim`：支持状态、重试时间、租约和创建时间的批量抢占。

## Agent 分析实现（V5）

`V5__agent_analysis.sql` 不修改 V1～V4，增加 `agent_run.execution_count`、`idx_agent_run_claim`、`ai_analysis_result.category_code`，并注册 `agent:read`、`agent:rerun` 权限；开发环境为 ADMIN/SUPPORT 授权。

- `agent_run` 的 `(ticket_id, run_type, input_revision, attempt_no)` 唯一键保证事件重复消费时不会重复建 Run。人工重跑锁定 Ticket 行后计算下一个 `attempt_no`。
- Worker 通过 `FOR UPDATE SKIP LOCKED` 抢占 PENDING 或租约过期的 RUNNING；每次抢占更新 `worker_id`、`lease_until`、`execution_count` 和 `version`。旧 Worker 提交时必须通过所有权/租约检查和乐观锁检查。
- 模型调用没有数据库事务；成功时步骤、分析结果和 Run 状态在同一短事务提交，失败时步骤和 FAILED 状态在另一短事务提交。失败不写 Ticket。
- `agent_step.input_snapshot`、`output_snapshot` 仅存摘要；`ai_analysis_result.category_code` 保存模型分类枚举，`suggested_category_id` 保持空值，待正式分类映射功能实现。
- `ai_analysis_result` 只保存成功 Run 的结果；HIGH 风险或低置信度会强制 `manual_required=true`。查询当前分析返回最近一次成功结果，而不是失败重跑结果。

## 知识库文本检索（V6）

`V6__knowledge_text_search.sql` 为 `knowledge_chunk.content` 增加 `WITH PARSER ngram` 的 FULLTEXT 索引，并增加 `token_count_estimated` 标记；同时注册 `knowledge:manage`、`knowledge:search` 权限。开发 ADMIN 同时拥有管理和检索权限，SUPPORT 仅可检索。V1～V5 不作修改。

- 文章 `DRAFT → PUBLISHED → WITHDRAWN`；版本 `DRAFT → PUBLISHED → SUPERSEDED`。文章 `current_version_id` 指向唯一当前发布版本；已发布版本内容不原地修改。
- 发布时以 `SELECT ... FOR UPDATE` 锁定文章，生成 `knowledge_chunk`、将旧版本标记为 SUPERSEDED、发布目标版本并切换 `current_version_id`，全部在同一事务。重复发布当前版本直接返回，不增加分块。
- 全文搜索同时限定 article/version/chunk 的 `tenant_id`、文章与版本状态、`current_version_id`、`valid_from` 和排他的 `valid_until`；仅使用绑定参数，不拼接用户查询。
- 有效期参数统一以 UTC `LocalDateTime` 绑定 MySQL `DATETIME(6)`，避免应用服务器本地时区导致未来有效期被误判为过期。
- 按 Unicode 码点固定分块：窗口 500、重叠 50，最大 20,000 码点和 50 片。`chunk_index` 从 0 开始。`token_count` 是启发式估算：中日韩码点每个计 1，其他码点每 4 个计 1 并向上取整；不能视为模型真实 Token 用量。

## AI 回复建议与引用（V7）

`V7__reply_suggestion_review.sql` 扩展 V1 已预留的 `ai_suggestion` 和 `knowledge_citation`：建议新增 `edited_content`、`edited_by/at`、`rejected_by/at`、`rejection_reason`，引用新增 `tenant_id` 并回填既有记录。`ai_suggestion.run_id` 唯一，`agent_run(ticket_id,run_type,input_revision,attempt_no)` 唯一；建议 `version` 用 JPA 乐观锁加 API `expectedVersion` 防止并发编辑、采纳、拒绝。`original_content` 不更新；采纳时写 `final_content_snapshot`，不写 `ticket_message`。

建议状态 `READY → EDITED → ADOPTED/REJECTED`，也允许 `READY → ADOPTED/REJECTED`；新建议会将旧 `READY/EDITED` 标记为 `SUPERSEDED`。检索候选引用持久化时记录租户、文章/版本/片段 ID、检索分数、顺序、内容快照和是否被模型引用。模型输出的引用必须是检索结果子集，并在正文中有相应标记；写入和采纳前都重新检查当前发布版本、文章状态及有效期。检索候选还必须覆盖工单标题至少 80% 的非空白双字组合，避免仅凭通用词命中生成建议。V7 注册 `suggestion:read` 和 `suggestion:review`，开发 ADMIN/SUPPORT 获授权。无可靠检索或校验失败不产生建议，回复 Run 失败并等待人工处理。

可靠性边界：

- 工单与 PENDING Outbox 在同一 MySQL 事务提交。
- 发布器使用 `FOR UPDATE SKIP LOCKED` 短事务抢占，RabbitMQ 网络调用在事务外执行。
- 只有 publisher confirm ACK 且消息未被 mandatory return 时才标记 PUBLISHED。
- 发布失败增加 `retry_count` 并设置指数退避的 `next_retry_at`，达到上限后保留 FAILED 且停止调度。
- 消费者以 `(event_id, consumer_name)` 原子占位；业务处理与 PROCESSED 更新在同一事务。
- 数据库事务提交后才手动 ACK；ACK 丢失引起的重复投递会命中 PROCESSED Inbox 并跳过业务处理。
- 可重试消费异常在有限次数内 requeue，不可重试或超过次数后通过 RabbitMQ DLX 进入 DLQ。
