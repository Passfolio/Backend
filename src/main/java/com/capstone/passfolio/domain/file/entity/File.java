package com.capstone.passfolio.domain.file.entity;

import com.capstone.passfolio.common.auditor.UserBaseEntity;
import com.capstone.passfolio.domain.file.entity.enums.MediaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * S3 multipart 업로드가 완료된 파일의 메타 데이터.
 *
 * <p>UploadId/parts 등 진행 중 상태는 S3 자체에 위임하고, 본 엔티티는 업로드 성공 후
 * {@code HeadObject} 로 실제 크기까지 검증된 시점에만 영속화된다(설계 §FileService 참조).
 *
 * <p>{@link UserBaseEntity} 를 상속해 {@code createdAt / lastModifiedAt / createdBy / lastModifiedBy}
 * 감사 컬럼을 자동 채운다. {@code createdBy} 는 후속 IDOR 가드(소유자 검증) 의 근거로 사용된다.
 */
@Entity
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(
        name = "files",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_FILES_S3_OBJECT_KEY", columnNames = "s3_object_key")
        }
)
public class File extends UserBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 서버에서 생성한 S3 object key ({@code {subfolder}/{UUID}__{sanitized_filename}} 형식). */
    @Column(name = "s3_object_key", nullable = false, length = 1024, unique = true)
    private String s3ObjectKey;

    /** 클라이언트가 업로드 시 전달한 원본 파일명(표시용). 보안상 이 값이 S3 key 에 직접 들어가지는 않는다. */
    @Column(name = "filename", nullable = false, length = 1024)
    private String filename;

    /** 업로드된 파일의 실제 크기(바이트). post-upload {@code HeadObject} 검증을 통과한 값이다. */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** MIME prefix / 확장자로부터 분류된 미디어 카테고리. */
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 16)
    private MediaType mediaType;
}
