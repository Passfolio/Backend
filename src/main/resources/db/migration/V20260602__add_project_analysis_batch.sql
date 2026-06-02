ALTER TABLE project_analysis ADD COLUMN batch_id VARCHAR(64);
ALTER TABLE project_analysis ADD COLUMN mode VARCHAR(16);

CREATE INDEX idx_project_analysis_batch_id ON project_analysis (batch_id);
