-- FK: user_id → users(id)
ALTER TABLE ai_jobs
    ADD CONSTRAINT fk_ai_jobs_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- FK: input_file_id → files(id)
ALTER TABLE ai_jobs
    ADD CONSTRAINT fk_ai_jobs_input_file_id
        FOREIGN KEY (input_file_id) REFERENCES files (id) ON DELETE SET NULL;

-- 복합 인덱스: stale-job 스케줄러 쿼리 성능
-- WHERE status = 'PENDING' AND created_at < :cutoff
CREATE INDEX idx_ai_jobs_status_created_at ON ai_jobs (status, created_at);
