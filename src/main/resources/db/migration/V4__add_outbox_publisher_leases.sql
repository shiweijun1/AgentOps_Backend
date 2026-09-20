ALTER TABLE outbox_event
    ADD COLUMN locked_by VARCHAR(128) NULL AFTER published_at,
    ADD COLUMN locked_until DATETIME(6) NULL AFTER locked_by,
    ADD COLUMN last_error VARCHAR(1000) NULL AFTER locked_until,
    ADD KEY idx_outbox_claim (status, next_retry_at, locked_until, created_at);
