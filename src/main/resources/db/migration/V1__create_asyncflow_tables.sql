CREATE TABLE async_task (
    task_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    simulate_failures INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (task_id),
    CONSTRAINT uk_async_task_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_async_task_status_updated ON async_task(status, updated_at);

CREATE TABLE task_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id VARCHAR(36) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_task_event_task_time ON task_event(task_id, occurred_at);

CREATE TABLE outbox_event (
    event_id VARCHAR(36) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    publish_attempts INT NOT NULL DEFAULT 0,
    PRIMARY KEY (event_id)
);

CREATE INDEX idx_outbox_unpublished ON outbox_event(published_at, created_at);

CREATE TABLE test_run (
    run_id VARCHAR(64) NOT NULL,
    suite VARCHAR(32) NOT NULL,
    environment VARCHAR(64) NOT NULL,
    git_commit VARCHAR(40) NULL,
    total_count INT NOT NULL,
    passed_count INT NOT NULL,
    failed_count INT NOT NULL,
    duration_ms BIGINT NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    finished_at TIMESTAMP(6) NULL,
    PRIMARY KEY (run_id)
);

CREATE TABLE failure_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(64) NOT NULL,
    case_id VARCHAR(128) NOT NULL,
    failure_type VARCHAR(32) NOT NULL,
    evidence LONGTEXT NOT NULL,
    infrastructure_failure BOOLEAN NOT NULL DEFAULT FALSE,
    historical_occurrences INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_failure_run_case ON failure_record(run_id, case_id);
