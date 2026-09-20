# AgentOps Backend

AgentOps 是一个企业智能工单处理平台。本仓库提供 Java 21、Spring Boot 3 的模块化单体后端，以及身份认证、RBAC、工单领域、Transactional Outbox、工单智能分析 Agent、知识库文本检索和带引用的 AI 回复建议最小闭环。建议仅供客服人工审核，不自动发送客户消息。

## 技术栈

- Java 21
- Spring Boot 3.5
- Maven
- MySQL 8.4
- Redis 7.4
- RabbitMQ 4
- Spring Security + JWT Access Token
- Flyway
- springdoc-openapi
- JUnit 5 + Testcontainers

## 模块边界

```text
com.agentops
├── bootstrap       启动与 OpenAPI 配置
├── identity        身份认证、RBAC、团队
├── ticket          工单、分派、会话
├── agent           Agent Run、Step、分析和建议
├── knowledge       知识内容与检索
├── analytics       指标读模型
├── audit           操作审计
├── infrastructure  消息、缓存和外部适配器
└── shared          API、异常、持久化等通用能力
```

模块之间后续应通过应用服务接口或领域事件协作，不允许跨模块直接调用对方 Repository。

## 本地启动

### 1. 环境要求

- JDK 21
- Maven 3.9+
- Docker Desktop 或兼容的 Docker Engine

确认版本：

```powershell
java -version
mvn -version
docker version
```

### 2. 准备环境变量

PowerShell：

```powershell
Copy-Item .env.example .env
```

`.env` 中的密码仅供本地开发。部署前必须替换数据库、RabbitMQ 和 JWT 密钥。

### 3. 启动基础设施

```powershell
docker compose up -d
docker compose ps
```

默认端口：

| 服务 | 地址 |
|---|---|
| MySQL | `localhost:3307`（容器内仍为 `3306`） |
| Redis | `localhost:6379` |
| RabbitMQ | `localhost:5672` |
| RabbitMQ 控制台 | `http://localhost:15672` |

### 4. 编译与测试

```powershell
mvn clean verify
```

Testcontainers 测试在 Docker 可用时启动 MySQL、Redis 和 RabbitMQ；Docker 不可用时相关集成测试会跳过，请检查测试报告的 `Skipped` 值。Windows Docker Desktop 如不能被 Java 自动发现，可在当前 PowerShell 会话设置 `$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'` 后重跑。

### 5. 启动应用

```powershell
mvn spring-boot:run
```

启动时 Flyway 自动执行 `db/migration` 中的迁移，Hibernate 仅执行 schema 校验。

启动后可访问：

- 健康检查：`http://localhost:8080/actuator/health`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

### 6. 使用开发管理员登录

本地 Profile 默认通过 Flyway V2 初始化以下开发账号：

| 配置 | 值 |
|---|---|
| 租户 | `default` |
| 用户名 | `admin` |
| 密码 | `Admin@123456` |
| 角色 | `ADMIN` |
| 权限 | 管理与全部工单权限 |

该密码只用于本地开发，数据库中保存的是 BCrypt 哈希。生产或共享环境必须设置 `DEV_SEED_ENABLED=false`，并替换 `JWT_SECRET`。

PowerShell 登录示例：

```powershell
$loginBody = @{
  tenantId = 'default'
  username = 'admin'
  password = 'Admin@123456'
} | ConvertTo-Json

$login = Invoke-RestMethod `
  -Method Post `
  -Uri 'http://localhost:8080/api/v1/auth/login' `
  -ContentType 'application/json' `
  -Body $loginBody

$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/auth/me' -Headers $headers
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/admin/security-check' -Headers $headers
```

## 身份认证接口

| 方法 | 地址 | 访问要求 |
|---|---|---|
| `POST` | `/api/v1/auth/login` | 公开；签发 Access Token |
| `GET` | `/api/v1/auth/me` | 有效 Bearer Token |
| `GET` | `/api/v1/admin/security-check` | `admin:security-check` 权限 |

Access Token 默认有效期为 15 分钟，由 `JWT_ACCESS_TTL` 配置。过期 Token 返回 HTTP 401 和错误码 `AUTH_TOKEN_EXPIRED`；签名错误、格式错误或用户已失效返回 `AUTH_TOKEN_INVALID`；未携带 Token 返回 `AUTH_401`。

JWT 仅携带用户 ID、租户和 Token 类型。服务端在每次认证请求中重新加载用户状态、角色和权限，因此禁用用户或撤销权限无需等待 Access Token 过期。日志不会输出密码或完整 Token。

## 配置说明

默认配置位于：

- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`

常用环境变量包括 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`RABBITMQ_HOST`、`JWT_SECRET`、`JWT_ACCESS_TTL`、`DEFAULT_TENANT_ID` 和 `DEV_SEED_ENABLED`。

`local` Profile 会通过 `spring.config.import` 自动读取项目根目录的 `.env`，因此从 IDEA 启动时不需要重复填写同一组本地变量。`.env` 已加入 `.gitignore`，不得提交真实密码。

MySQL 和 RabbitMQ 的初始化账号只在数据卷第一次创建时生效。已有数据卷不会因为修改 `.env` 自动修改数据库内部密码；这种情况下应在服务内修改账号密码，或在确认不需要本地数据后重新创建数据卷。

当前认证使用无状态 SecurityFilterChain 和 BCrypt。Controller 只依赖应用服务，不直接访问 Repository；权限接口通过 `@PreAuthorize` 和稳定权限编码鉴权。Refresh Token、密码修改、用户管理接口和权限缓存不属于本阶段范围。

## 工单领域

Flyway V3 为本地环境增加以下演示身份，密码均为 `Admin@123456`，数据库中只保存 BCrypt 哈希：

| 用户名 | 角色 | 数据范围 |
|---|---|---|
| `customer` | `CUSTOMER` | 创建工单、查看本人创建的工单 |
| `support` | `SUPPORT` | 查看默认客服组或分配给本人的工单、分派和流转 |
| `admin` | `ADMIN` | 查看租户内全部工单、分派和流转，可重新打开 CLOSED 工单 |

工单接口：

| 方法 | 地址 | 权限 |
|---|---|---|
| `POST` | `/api/v1/tickets` | `ticket:create`，必须携带 `Idempotency-Key` |
| `GET` | `/api/v1/tickets/{id}` | 任一工单读取权限，并执行数据范围校验 |
| `GET` | `/api/v1/tickets` | 任一工单读取权限，支持分页、状态、优先级、团队、负责人和关键字筛选 |
| `POST` | `/api/v1/tickets/{id}/assignments` | `ticket:assign` |
| `POST` | `/api/v1/tickets/{id}/transitions` | `ticket:transition` |
| `GET` | `/api/v1/tickets/{id}/assignments` | 任一工单读取权限 |
| `GET` | `/api/v1/tickets/{id}/transitions` | 任一工单读取权限 |

分派和状态流转请求必须提交详情响应中的 `version` 作为 `expectedVersion`。版本过期或数据库发生并发更新时返回 HTTP 409 与 `TICKET_VERSION_CONFLICT`。

合法状态转换为：

```text
NEW -> PENDING -> PROCESSING -> WAITING_CUSTOMER
                    ^                 |
                    +-----------------+
PROCESSING -> RESOLVED -> CLOSED
                    |         |
                    +---------+-> PROCESSING（重新打开）
```

`RESOLVED → PROCESSING` 和 `CLOSED → PROCESSING` 会增加 `reopenCount`；后者仅允许 `ADMIN`。非法转换返回 HTTP 409 与 `TICKET_INVALID_TRANSITION`。

完整的 IntelliJ HTTP Client 演示见 `docs/agentops-api.http`。

## 可靠异步事件

工单创建事务会同时写入 `TicketCreatedEvent` Outbox，不会在请求事务中直接调用 RabbitMQ：

```text
创建事务: ticket + transition + idempotency + outbox(PENDING)
                                      |
                                      v
定时发布器: SKIP LOCKED 抢占 -> RabbitMQ publisher confirm -> PUBLISHED
                                      |
                                      v
消费者: Inbox 占位 -> 业务 Handler -> PROCESSED -> 手动 ACK
```

RabbitMQ 拓扑：

| 类型 | 名称/路由 |
|---|---|
| Topic Exchange | `agentops.events` |
| 主队列 | `agentops.ticket-created` |
| Routing Key | `ticket.created.v1` |
| Dead Letter Exchange | `agentops.dlx` |
| DLQ | `agentops.ticket-created.dlq` |
| DLQ Routing Key | `ticket.created.dead` |

Outbox 状态为 `PENDING → PROCESSING → PUBLISHED`；发布失败进入 `FAILED`，在 `nextRetryAt` 到期后有限重试。发布器使用带租约的 `FOR UPDATE SKIP LOCKED` 抢占，进程异常后可由其他实例在租约过期后接管。

消费者采用手动 ACK。成功提交 Inbox 与业务事务后 ACK；可重试错误在次数内 requeue；不可重试错误或超过次数后 reject，由 RabbitMQ 路由到 DLQ。同一 `eventId + consumerName` 已为 PROCESSED 时跳过业务处理并直接 ACK。

默认可靠消息参数可通过以下环境变量调整：

- `MESSAGING_ENABLED`
- `OUTBOX_BATCH_SIZE`
- `OUTBOX_MAX_RETRIES`
- `OUTBOX_PUBLISH_INTERVAL`
- `OUTBOX_LEASE_DURATION`
- `OUTBOX_CONFIRM_TIMEOUT`
- `INBOX_MAX_RETRIES`

事件和日志只记录必要元数据；日志不会输出完整消息正文。当前 `TicketCreatedEventHandler` 只在 Inbox 事务中幂等登记 `AgentRun(PENDING)`，不调用模型。

## 工单智能分析 Agent

```text
TicketCreatedEvent → Inbox + AgentRun(PENDING) → Worker 租约抢占(RUNNING)
  → 内容预处理 → 分类/优先级/情绪分析 → 风险与人工复核判定
  → AgentStep + AiAnalysisResult + AgentRun(SUCCEEDED)
  └─ 超时、非法 JSON 或其他执行失败 → AgentStep + AgentRun(FAILED)
```

Worker 使用 `FOR UPDATE SKIP LOCKED` 短事务抢占。每次抢占产生独立的所有权标识，租约过期后其他实例可接管；旧 Worker 的结果会被拒绝。模型 HTTP 调用不在数据库事务中，结果和最终状态在受租约保护的短事务中提交。失败不修改原始工单。`executionCount` 表示 Worker 实际接管次数；人工重新分析创建新 Run 并递增 `attemptNo`。

步骤包含 `CONTENT_PREPROCESSING`、`TICKET_CLASSIFICATION`、`RISK_EVALUATION`、`RESULT_PERSISTENCE`。步骤的输入输出只存长度、分类、风险等 JSON 摘要，不存工单全文。模型 JSON 使用严格枚举、必填字段、未知字段、重复字段、置信度范围校验；`HIGH` 风险或 `confidence < 0.7` 强制 `manualRequired=true`。

| 方法 | 地址 | 权限 |
|---|---|---|
| `GET` | `/api/v1/tickets/{ticketId}/agent-runs` | `agent:read` + 工单可见范围 |
| `GET` | `/api/v1/agent-runs/{runId}` | `agent:read` + 工单可见范围 |
| `GET` | `/api/v1/agent-runs/{runId}/steps` | `agent:read` + 工单可见范围 |
| `GET` | `/api/v1/tickets/{ticketId}/analysis` | `agent:read` + 工单可见范围 |
| `POST` | `/api/v1/tickets/{ticketId}/agent-runs` | `agent:rerun` + 工单可见范围 |

V5 为开发环境的 `admin` 和 `support` 角色授予 Agent 权限。默认 `AI_PROVIDER=fake`，无需 API Key 即可演示；`disabled` 会使分析 Run 失败但不影响应用启动；`http` 使用兼容 Chat Completions 的 HTTP 接口。HTTP 模式必须配置 `AI_BASE_URL`（完整端点，例如 `/v1/chat/completions`）、`AI_API_KEY`、`AI_MODEL`，API Key 只从配置读取且不写入数据库或日志。

可配置项：`AGENT_WORKER_ENABLED`、`AGENT_BATCH_SIZE`、`AGENT_POLL_INTERVAL`、`AGENT_LEASE_DURATION`、`AGENT_MODEL_TIMEOUT`、`AGENT_LOW_CONFIDENCE_THRESHOLD`、`AI_REQUEST_TIMEOUT`。租约必须比模型超时至少长 5 秒。模型超时会取消本次调用并使 Run 失败。

本地演示：启动基础设施和应用后，使用上面的管理员登录示例获得 `$headers`，再执行：

```powershell
$ticket = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/tickets' `
  -Headers ($headers + @{'Idempotency-Key' = [guid]::NewGuid().ToString()}) `
  -ContentType 'application/json' `
  -Body '{"title":"无法登录","description":"企业账号无法登录，提示认证失败","priority":"MEDIUM"}'
$ticketId = $ticket.data.id
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/tickets/$ticketId/agent-runs" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/tickets/$ticketId/analysis" -Headers $headers
```

事件发布和 Worker 执行是异步的；若首次查询仍为 `PENDING`，稍等后再次查询。手动重新分析可执行 `Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/tickets/$ticketId/agent-runs" -Headers $headers`。步骤 API 使用运行列表返回的 `id`。

## 知识库文本检索

本阶段只管理手工录入的文本。持有 `knowledge:manage` 的管理员创建文章与草稿版本，并以发布命令完成审核发布；SUPPORT 仅有 `knowledge:search` 权限。权限只控制接口，所有知识库读写和检索还会使用当前登录用户的 `tenantId` 做数据隔离。

| 方法 | 地址 | 权限 |
|---|---|---|
| `POST` | `/api/v1/knowledge/articles` | `knowledge:manage` |
| `POST` | `/api/v1/knowledge/articles/{id}/versions` | `knowledge:manage` |
| `POST` | `/api/v1/knowledge/articles/{id}/versions/{versionId}/publish` | `knowledge:manage` |
| `POST` | `/api/v1/knowledge/articles/{id}/withdraw` | `knowledge:manage` |
| `GET` | `/api/v1/knowledge/articles/{id}` | `knowledge:manage` |
| `GET` | `/api/v1/knowledge/articles/{id}/versions` | `knowledge:manage` |
| `GET` | `/api/v1/knowledge/search?q=登录&limit=10` | `knowledge:search` |

文章状态为 `DRAFT → PUBLISHED → WITHDRAWN`，撤回后不能直接恢复。版本状态为 `DRAFT → PUBLISHED → SUPERSEDED`；已发布版本不可原地修改，新内容必须新建版本。发布当前版本为幂等操作，不会重复生成片段。发布新版本时，旧版本转为 `SUPERSEDED`，检索只返回当前版本、已发布且未撤回、处于 `validFrom` / `validUntil` 有效期内的片段。`validUntil` 为排他上界。

分块固定为 500 个 Unicode 码点，片段间重叠 50 个码点，`chunkIndex` 从 0 开始；单篇上限 20,000 码点，最多 50 片，绝不在代理对（如 emoji）中间截断。`tokenCount` **只是估算值**：每个中日韩码点计 1，其他码点每 4 个计 1 并向上取整；`tokenCountEstimated=true`，不用于模型计费。发布、分块、旧版状态及当前版本切换位于同一 MySQL 事务中。

V6 为 `knowledge_chunk.content` 建立 MySQL `ngram` FULLTEXT 索引；默认 MySQL `ngram_token_size=2`，因此中文查询建议至少两个字。搜索使用绑定参数的 `MATCH ... AGAINST`，按 MySQL 相关性分数排序，不拼接用户输入。返回 `articleId`、`versionId`、`chunkId`、标题、片段和分数。此分数仅供文本检索排序，不是语义相似度。

完整可执行示例见 [HTTP Client 演示](docs/agentops-api.http)。本阶段不含 PDF 上传或 Embedding。

## 带知识引用的 AI 回复建议（V7）

分析 Run 成功后在同一事务中幂等创建 `REPLY_SUGGESTION` Run。独立 Worker 租约抢占后，先检查分析风险与置信度，使用租户限定的当前有效知识全文检索，再在数据库事务外调用回复模型。模型只能引用实际检索到的片段，JSON 必须包含正文、`citationChunkIds` 和 0–1 置信度；正文中每个引用使用 `[citation:chunkId]`。无命中、高风险、低置信度、模型超时或不可验证输出均使回复 Run 进入 FAILED，留给人工处理，不产生建议。

成功时，短事务重新锁定并校验片段仍为当前、已发布、未撤回且在有效期内；`ai_suggestion`、`knowledge_citation`、步骤和 Run 成功状态一起提交。所有检索候选片段都记录快照，`usedInAnswer` 指明真正引用的片段。新建议会将同一工单旧的未审核建议标为 `SUPERSEDED`。客服可查看、编辑、采纳或拒绝：`READY → EDITED → ADOPTED/REJECTED`，也可直接 `READY → ADOPTED/REJECTED`。人工修改保留 `originalContent`，采纳时另存 `finalContentSnapshot`；编辑必须保留已验证引用且不能加入虚构引用。审核命令带 `expectedVersion`，冲突返回 409。采纳时再次校验知识有效性及工单内容版本。采纳只记录结果，**不会创建客户消息或自动发送**。

| 方法 | 地址 | 权限 |
|---|---|---|
| `GET` | `/api/v1/tickets/{ticketId}/suggestions` | `suggestion:read` + 工单可见范围 + 客服/管理员 |
| `GET` | `/api/v1/suggestions/{id}` | 同上 |
| `PATCH` | `/api/v1/suggestions/{id}` | `suggestion:review`，正文与 `expectedVersion` |
| `POST` | `/api/v1/suggestions/{id}/adopt` | `suggestion:review`，`expectedVersion` |
| `POST` | `/api/v1/suggestions/{id}/reject` | `suggestion:review`，原因与 `expectedVersion` |

V7 增加人工编辑/拒绝快照列、引用租户列及权限，不修改 V1～V6。默认 fake 模型可本地演示；HTTP 模型沿用 `AI_PROVIDER=http` 与现有模型配置。MySQL ngram 分数是关键词相关性，不是事实正确性证明；客服采纳前仍需人工核实内容。演示流程见 [HTTP Client 示例](docs/agentops-api.http)。

## 数据库设计

数据库说明见 `docs/database-design.md`，基线 DDL 见 `src/main/resources/db/migration/V1__baseline_schema.sql`；V2～V7 依次覆盖开发身份、工单、Outbox、Agent 分析、知识检索及回复建议。Hibernate 保持 `ddl-auto: validate`，所有数据库变更必须通过新的 Flyway 迁移完成。
