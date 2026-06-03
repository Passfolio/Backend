CREATE TABLE repo_availability
(
    id               VARCHAR(64) PRIMARY KEY,
    user_id          BIGINT        NOT NULL,
    repo_url         VARCHAR(2048) NOT NULL,
    status           VARCHAR(16)   NOT NULL DEFAULT 'CHECKING',
    worktree_mb      DOUBLE PRECISION,
    git_history_mb   DOUBLE PRECISION,
    reason           TEXT,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    last_modified_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_repo_availability_user_repo UNIQUE (user_id, repo_url)
);

CREATE INDEX idx_repo_availability_user_id ON repo_availability (user_id);
