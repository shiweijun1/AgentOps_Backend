CREATE TABLE team (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_tenant_name (tenant_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_user (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NULL,
    team_id BINARY(16) NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_tenant_username (tenant_id, username),
    KEY idx_user_team (team_id),
    CONSTRAINT fk_user_team FOREIGN KEY (team_id) REFERENCES team (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_role (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_tenant_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_permission (
    id BINARY(16) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_role (
    user_id BINARY(16) NOT NULL,
    role_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES iam_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE role_permission (
    role_id BINARY(16) NOT NULL,
    permission_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES iam_role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES iam_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_category (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_tenant_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    ticket_no VARCHAR(32) NOT NULL,
    requester_id BINARY(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    category_id BINARY(16) NULL,
    priority VARCHAR(16) NOT NULL,
    sentiment VARCHAR(32) NULL,
    risk_level VARCHAR(16) NULL,
    routing_type VARCHAR(32) NOT NULL,
    manual_reason VARCHAR(64) NULL,
    team_id BINARY(16) NULL,
    assignee_id BINARY(16) NULL,
    content_revision INT NOT NULL DEFAULT 1,
    content_fingerprint CHAR(64) NOT NULL,
    possible_duplicate_of BINARY(16) NULL,
    reopen_count INT NOT NULL DEFAULT 0,
    first_response_at DATETIME(6) NULL,
    resolved_at DATETIME(6) NULL,
    closed_at DATETIME(6) NULL,
    submitted_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_tenant_no (tenant_id, ticket_no),
    KEY idx_ticket_requester_created (tenant_id, requester_id, created_at),
    KEY idx_ticket_queue (tenant_id, status, team_id, priority, created_at),
    KEY idx_ticket_assignee_status (tenant_id, assignee_id, status),
    KEY idx_ticket_fingerprint (tenant_id, requester_id, content_fingerprint, created_at),
    CONSTRAINT fk_ticket_requester FOREIGN KEY (requester_id) REFERENCES app_user (id),
    CONSTRAINT fk_ticket_category FOREIGN KEY (category_id) REFERENCES ticket_category (id),
    CONSTRAINT fk_ticket_team FOREIGN KEY (team_id) REFERENCES team (id),
    CONSTRAINT fk_ticket_assignee FOREIGN KEY (assignee_id) REFERENCES app_user (id),
    CONSTRAINT fk_ticket_duplicate FOREIGN KEY (possible_duplicate_of) REFERENCES ticket (id),
    CONSTRAINT chk_ticket_content_revision CHECK (content_revision >= 1),
    CONSTRAINT chk_ticket_reopen_count CHECK (reopen_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_message (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    ticket_id BINARY(16) NOT NULL,
    sender_type VARCHAR(32) NOT NULL,
    sender_id VARCHAR(64) NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    source_suggestion_id BINARY(16) NULL,
    client_request_id VARCHAR(128) NOT NULL,
    visible_to_requester BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_message_request (tenant_id, ticket_id, client_request_id),
    KEY idx_ticket_message_timeline (ticket_id, created_at),
    CONSTRAINT fk_ticket_message_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_attachment (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    ticket_id BINARY(16) NOT NULL,
    message_id BINARY(16) NULL,
    original_filename VARCHAR(255) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_hash CHAR(64) NOT NULL,
    scan_status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_attachment_ticket (ticket_id),
    CONSTRAINT fk_attachment_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id),
    CONSTRAINT fk_attachment_message FOREIGN KEY (message_id) REFERENCES ticket_message (id),
    CONSTRAINT chk_attachment_file_size CHECK (file_size >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_assignment_record (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    ticket_id BINARY(16) NOT NULL,
    assignment_type VARCHAR(32) NOT NULL,
    from_team_id BINARY(16) NULL,
    from_assignee_id BINARY(16) NULL,
    to_team_id BINARY(16) NULL,
    to_assignee_id BINARY(16) NULL,
    operator_id VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    ticket_version BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_assignment_ticket_time (ticket_id, occurred_at),
    CONSTRAINT fk_assignment_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_transition_record (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    ticket_id BINARY(16) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NULL,
    command_id VARCHAR(128) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_transition_command (tenant_id, ticket_id, command_id),
    KEY idx_transition_ticket_time (ticket_id, occurred_at),
    CONSTRAINT fk_transition_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_article (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    category_id BINARY(16) NULL,
    status VARCHAR(32) NOT NULL,
    current_version_id BINARY(16) NULL,
    valid_from DATETIME(6) NULL,
    valid_until DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_knowledge_status_category (tenant_id, status, category_id),
    CONSTRAINT fk_knowledge_category FOREIGN KEY (category_id) REFERENCES ticket_category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_version (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    article_id BINARY(16) NOT NULL,
    version_no INT NOT NULL,
    content LONGTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    published_at DATETIME(6) NULL,
    published_by VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_version (article_id, version_no),
    CONSTRAINT fk_knowledge_version_article FOREIGN KEY (article_id) REFERENCES knowledge_article (id),
    CONSTRAINT chk_knowledge_version_no CHECK (version_no >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE knowledge_article
    ADD CONSTRAINT fk_knowledge_current_version
    FOREIGN KEY (current_version_id) REFERENCES knowledge_version (id);

CREATE TABLE knowledge_chunk (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    knowledge_version_id BINARY(16) NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    token_count INT NOT NULL,
    embedding_ref VARCHAR(255) NULL,
    index_status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_chunk_index (knowledge_version_id, chunk_index),
    CONSTRAINT fk_chunk_version FOREIGN KEY (knowledge_version_id) REFERENCES knowledge_version (id),
    CONSTRAINT chk_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT chk_chunk_token_count CHECK (token_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_run (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    ticket_id BINARY(16) NOT NULL,
    run_type VARCHAR(32) NOT NULL,
    input_revision INT NOT NULL,
    attempt_no INT NOT NULL,
    parent_run_id BINARY(16) NULL,
    status VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    model_provider VARCHAR(64) NULL,
    model_name VARCHAR(128) NULL,
    prompt_version VARCHAR(64) NULL,
    worker_id VARCHAR(128) NULL,
    lease_until DATETIME(6) NULL,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    estimated_cost DECIMAL(18, 8) NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_run_attempt (ticket_id, run_type, input_revision, attempt_no),
    KEY idx_agent_run_status_created (tenant_id, status, created_at),
    CONSTRAINT fk_agent_run_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id),
    CONSTRAINT fk_agent_run_parent FOREIGN KEY (parent_run_id) REFERENCES agent_run (id),
    CONSTRAINT chk_agent_run_revision CHECK (input_revision >= 1),
    CONSTRAINT chk_agent_run_attempt CHECK (attempt_no >= 1),
    CONSTRAINT chk_agent_run_tokens CHECK (input_tokens >= 0 AND output_tokens >= 0),
    CONSTRAINT chk_agent_run_cost CHECK (estimated_cost >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_step (
    id BINARY(16) NOT NULL,
    run_id BINARY(16) NOT NULL,
    step_type VARCHAR(64) NOT NULL,
    sequence_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_snapshot JSON NULL,
    output_snapshot JSON NULL,
    model_name VARCHAR(128) NULL,
    prompt_version VARCHAR(64) NULL,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_step_sequence (run_id, sequence_no),
    CONSTRAINT fk_agent_step_run FOREIGN KEY (run_id) REFERENCES agent_run (id),
    CONSTRAINT chk_agent_step_values CHECK (
        sequence_no >= 0 AND input_tokens >= 0 AND output_tokens >= 0 AND retry_count >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_analysis_result (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    ticket_id BINARY(16) NOT NULL,
    run_id BINARY(16) NOT NULL,
    input_revision INT NOT NULL,
    suggested_category_id BINARY(16) NULL,
    suggested_priority VARCHAR(16) NULL,
    sentiment VARCHAR(32) NULL,
    risk_level VARCHAR(16) NOT NULL,
    confidence DECIMAL(5, 4) NOT NULL,
    reason VARCHAR(1000) NULL,
    manual_required BOOLEAN NOT NULL,
    manual_reason VARCHAR(64) NULL,
    applied_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_analysis_run (run_id),
    CONSTRAINT fk_analysis_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id),
    CONSTRAINT fk_analysis_run FOREIGN KEY (run_id) REFERENCES agent_run (id),
    CONSTRAINT fk_analysis_category FOREIGN KEY (suggested_category_id) REFERENCES ticket_category (id),
    CONSTRAINT chk_analysis_confidence CHECK (confidence >= 0 AND confidence <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_suggestion (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    ticket_id BINARY(16) NOT NULL,
    run_id BINARY(16) NOT NULL,
    input_revision INT NOT NULL,
    original_content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    confidence DECIMAL(5, 4) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    superseded_by BINARY(16) NULL,
    adopted_by BINARY(16) NULL,
    adopted_at DATETIME(6) NULL,
    final_content_snapshot TEXT NULL,
    source_message_id BINARY(16) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_suggestion_run (run_id),
    KEY idx_suggestion_ticket_status (ticket_id, status, created_at),
    CONSTRAINT fk_suggestion_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id),
    CONSTRAINT fk_suggestion_run FOREIGN KEY (run_id) REFERENCES agent_run (id),
    CONSTRAINT fk_suggestion_superseded FOREIGN KEY (superseded_by) REFERENCES ai_suggestion (id),
    CONSTRAINT fk_suggestion_adopter FOREIGN KEY (adopted_by) REFERENCES app_user (id),
    CONSTRAINT fk_suggestion_message FOREIGN KEY (source_message_id) REFERENCES ticket_message (id),
    CONSTRAINT chk_suggestion_confidence CHECK (confidence >= 0 AND confidence <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_citation (
    id BINARY(16) NOT NULL,
    suggestion_id BINARY(16) NOT NULL,
    article_id BINARY(16) NOT NULL,
    knowledge_version_id BINARY(16) NOT NULL,
    chunk_id BINARY(16) NOT NULL,
    retrieval_score DECIMAL(8, 6) NOT NULL,
    rank_no INT NOT NULL,
    content_snapshot TEXT NOT NULL,
    used_in_answer BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_citation_rank (suggestion_id, rank_no),
    CONSTRAINT fk_citation_suggestion FOREIGN KEY (suggestion_id) REFERENCES ai_suggestion (id),
    CONSTRAINT fk_citation_article FOREIGN KEY (article_id) REFERENCES knowledge_article (id),
    CONSTRAINT fk_citation_version FOREIGN KEY (knowledge_version_id) REFERENCES knowledge_version (id),
    CONSTRAINT fk_citation_chunk FOREIGN KEY (chunk_id) REFERENCES knowledge_chunk (id),
    CONSTRAINT chk_citation_score CHECK (retrieval_score >= 0),
    CONSTRAINT chk_citation_rank CHECK (rank_no >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE operation_audit_log (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    before_snapshot JSON NULL,
    after_snapshot JSON NULL,
    reason VARCHAR(500) NULL,
    trace_id VARCHAR(64) NULL,
    client_ip VARCHAR(45) NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_target_time (tenant_id, target_type, target_id, occurred_at),
    KEY idx_audit_actor_time (tenant_id, actor_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE idempotency_record (
    id BINARY(16) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    requester_id VARCHAR(64) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    resource_type VARCHAR(64) NULL,
    resource_id VARCHAR(64) NULL,
    response_status INT NULL,
    response_body JSON NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_request (
        tenant_id, requester_id, operation_type, idempotency_key
    ),
    KEY idx_idempotency_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inbox_message (
    id BINARY(16) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    message_type VARCHAR(128) NOT NULL,
    payload JSON NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    received_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inbox_event_consumer (event_id, consumer_name),
    KEY idx_inbox_status_received (status, received_at),
    CONSTRAINT chk_inbox_retry CHECK (retry_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE outbox_event (
    id BINARY(16) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_dispatch (status, next_retry_at, created_at),
    CONSTRAINT chk_outbox_retry CHECK (retry_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
