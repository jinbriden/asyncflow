CREATE TABLE report_result (
    task_id VARCHAR(36) NOT NULL,
    report_name VARCHAR(100) NOT NULL,
    requested_by VARCHAR(100) NOT NULL,
    file_name VARCHAR(180) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    source_record_count INT NOT NULL,
    summary_row_count INT NOT NULL,
    total_quantity BIGINT NOT NULL,
    total_amount DECIMAL(19, 2) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    generated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (task_id),
    CONSTRAINT fk_report_result_task FOREIGN KEY (task_id) REFERENCES async_task(task_id)
);

CREATE INDEX idx_report_result_generated_at ON report_result(generated_at);
