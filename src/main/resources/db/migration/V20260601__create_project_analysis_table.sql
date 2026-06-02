CREATE TABLE project_analysis
(
    id               VARCHAR(64) PRIMARY KEY,
    repo_url         VARCHAR(2048) NOT NULL,
    analysis_flag    VARCHAR(16)   NOT NULL DEFAULT 'YET',
    user_id          BIGINT        NOT NULL,
    result_cdn_url   TEXT,
    service_name     VARCHAR(255),
    failure_reason   TEXT,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    last_modified_at TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_project_analysis_user_id ON project_analysis (user_id);
