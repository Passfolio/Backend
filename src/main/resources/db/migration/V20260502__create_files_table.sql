-- =============================================================================
-- V20260502__create_files_table.sql
--
-- Backlog: BE-file-aws-s3-multipart-upload-presigned-url-20260502T141354Z (T08)
-- Purpose: Persist metadata for files uploaded to S3 via the multipart presigned-URL
--          flow. Rows are inserted ONLY after a successful HeadObject size check
--          (FileService.completeMultipartUpload), so an existing row implies the
--          object is durable in S3.
--
-- Schema mirrors com.capstone.passfolio.domain.file.entity.File (T07) and
-- com.capstone.passfolio.common.auditor.UserBaseEntity (createdAt /
-- lastModifiedAt / createdBy / lastModifiedBy).
-- =============================================================================

CREATE TABLE files (
    id                  BIGSERIAL       PRIMARY KEY,

    -- Server-generated S3 object key: "{subfolder}/{UUID-36}__{sanitized_filename}".
    -- AWS allows S3 keys up to 1024 bytes, so VARCHAR(1024) matches the entity mapping.
    s3_object_key       VARCHAR(1024)   NOT NULL,

    -- Original client-supplied filename, kept for display only. Sanitization is applied
    -- separately when generating s3_object_key, so this column may contain UTF-8.
    filename            VARCHAR(1024)   NOT NULL,

    -- Actual byte size returned by S3 HeadObject after CompleteMultipartUpload.
    file_size           BIGINT          NOT NULL,

    -- com.capstone.passfolio.domain.file.entity.enums.MediaType (IMAGE/VIDEO/AUDIO/PDF/UNKNOWN).
    -- VARCHAR(16) gives headroom over the longest constant ("UNKNOWN" = 7 chars).
    media_type          VARCHAR(16)     NOT NULL,

    -- Audit columns inherited from UserBaseEntity (Spring Data JPA auditing populates these).
    created_at          TIMESTAMP,
    last_modified_at    TIMESTAMP,
    created_by          BIGINT,
    last_modified_by    BIGINT,

    -- Named unique constraint mirrors the entity's @UniqueConstraint(name="UK_FILES_S3_OBJECT_KEY")
    -- so Hibernate `validate` mode and Flyway agree, and DB error → ErrorCode mapping is stable.
    CONSTRAINT UK_FILES_S3_OBJECT_KEY UNIQUE (s3_object_key)
);
