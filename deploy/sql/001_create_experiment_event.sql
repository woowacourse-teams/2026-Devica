CREATE TABLE IF NOT EXISTS experiment_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    question_set_version VARCHAR(64) NOT NULL,
    event_name VARCHAR(48) NOT NULL,
    question_id VARCHAR(32) NULL,
    option_id VARCHAR(64) NULL,
    recommendation_os VARCHAR(32) NULL,
    recommendation_memory_gb INT NULL,
    recommendation_storage_gb INT NULL,
    recommendation_cpu_tier VARCHAR(64) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_experiment_event_session (session_id),
    INDEX idx_experiment_event_name_time (event_name, received_at),
    INDEX idx_experiment_event_question_time (question_id, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
