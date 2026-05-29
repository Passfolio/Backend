CREATE TABLE ai_jobs
(
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    ai_job_id          VARCHAR(64),
    type               VARCHAR(40)  NOT NULL,
    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    input_file_id      BIGINT,
    output_pdf_url  TEXT,
    error_message      TEXT,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_modified_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_jobs_user_id ON ai_jobs (user_id);
