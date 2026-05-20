package com.capstone.passfolio.domain.file.dto;

import com.capstone.passfolio.common.util.FileUrlUtils;
import com.capstone.passfolio.domain.file.entity.File;
import com.capstone.passfolio.domain.file.entity.enums.ActionType;
import com.capstone.passfolio.domain.file.entity.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 클라이언트 노출(REST) DTO 모음.
 *
 * <p>{@code S3Service} 의 내부 파라미터 캐리어인 {@link com.capstone.passfolio.domain.s3.dto.S3ServiceDto}
 * 와 분리되어 있다 — controller 레이어에 외부 입력만 검증/문서화 대상으로 두고, S3 SDK 호출 파라미터는
 * 내부 캐리어로 한 번 더 변환하기 위함이다 (file_security.md §3 — 신뢰 경계).
 *
 * <p>모든 nested 클래스는 프로젝트 표준인 {@code @Getter @Builder @NoArgsConstructor @AllArgsConstructor}
 * 패턴 ({@code DevSpecDto}, {@code S3ServiceDto} 와 동일) 을 따른다. {@code @Data} 는 {@code equals/hashCode}
 * 가 엔티티 동등성 의미를 흐릴 수 있어 도메인 DTO 에는 사용하지 않는다.
 *
 * <p>설계 참조: {@code 0001-harness-design.md} §"Files plan / FileDto" 와 §"Interface contracts (REST endpoints)".
 *
 * @see com.capstone.passfolio.domain.s3.dto.S3ServiceDto
 * @see com.capstone.passfolio.domain.file.entity.File
 */
public class FileDto {

    private FileDto() {
        // 정적 컨테이너. 인스턴스화 방지.
    }

    /**
     * {@code POST /api/v1/files/multipart/initiate} 요청 본문.
     *
     * <p>업로드 정책 (확장자 화이트리스트 + 100MB 상한) 검증의 입력. 클라이언트가 보낸 {@code originalFileName}
     * 은 표시용으로만 보존되며 S3 객체 키 생성에는 직접 사용하지 않는다 (file_security.md §3.2 — 경로 traversal 방어).
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "멀티파트 업로드 초기화 요청")
    public static class MultipartUploadInitiateRequest {

        @NotBlank(message = "originalFileName 은 비어있을 수 없습니다.")
        @Schema(description = "클라이언트가 표시할 원본 파일명. 확장자 검증의 기준값이지만 S3 키에는 직접 들어가지 않는다.",
                example = "large_video.mp4")
        private String originalFileName;

        @Schema(description = "클라이언트가 선언한 MIME 타입(선택). 누락 시 서버가 확장자에서 추론.",
                example = "video/mp4")
        private String mimeType;

        @NotNull(message = "fileSize 는 필수입니다.")
        @Min(value = 1, message = "fileSize 는 1바이트 이상이어야 합니다.")
        @Schema(description = "업로드 예정 파일의 정확한 크기(byte). 100MB 상한 검증 및 part 분할 계산에 사용.",
                example = "104857600")
        private Long fileSize;
    }

    /**
     * {@code POST /api/v1/files/multipart/initiate} 응답 본문.
     *
     * <p>{@code key} 와 {@code uploadId} 는 후속 모든 part / complete / abort 호출에 사용되는 식별자.
     * {@code partPresignedUrls} 는 일괄 발급 결과 — 클라이언트가 무결성(MD5) 결속이 필요하면 이 필드를
     * 무시하고 {@code /multipart/part-presigned-url} 을 part 마다 호출한다 (skill SKILL.md §4 참조).
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "멀티파트 업로드 초기화 응답")
    public static class MultipartUploadInitiateResponse {

        @Schema(description = "서버가 확정한 S3 객체 키 ({subfolder}/{UUID}__{sanitized}). 이후 모든 호출에서 그대로 사용.",
                example = "files/pdf/3f2d04ec-12ab-4cde-9abc-1234567890ab__resume.pdf")
        private String key;

        @Schema(description = "AWS S3 가 발급한 multipart upload 식별자.", example = "upload-id-12345")
        private String uploadId;

        @Schema(description = "S3 가 인지하는 Content-Type. 세션 생성 시 고정되어 변경 불가.",
                example = "video/mp4")
        private String contentType;

        @Schema(description = "총 파트 수. {@code calculatePartCount(fileSize)} 결과.",
                example = "5")
        private Integer partCount;

        @Schema(description = "전체 part presigned URL 일괄 발급 결과. " +
                "MD5 무결성 결속이 필요하면 이 필드를 무시하고 /multipart/part-presigned-url 을 part 마다 호출한다.")
        private List<PartPresignedUrl> partPresignedUrls;
    }

    /**
     * 일괄 발급된 단일 파트 presigned URL.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "일괄 발급된 단일 파트 presigned URL 정보")
    public static class PartPresignedUrl {

        @Schema(description = "1..10000 범위의 파트 번호.", example = "1")
        private Integer partNumber;

        @Schema(description = "S3 PUT presigned URL.", example = "https://...s3...amazonaws.com/...")
        private String presignedUrl;

        @Schema(description = "이 URL 에 PUT 가능한 정확한 본문 크기(byte). 클라이언트는 반드시 이 크기로 슬라이스해서 PUT 해야 한다.",
                example = "67108864")
        private Long contentLength;
    }

    /**
     * {@code POST /api/v1/files/multipart/part-presigned-url} 요청 본문.
     *
     * <p>클라이언트가 chunk 을 슬라이스 → MD5 계산 → 본 API 호출 의 무결성 강화 플로우용. {@code md5Base64}
     * 가 제공되면 서버는 presigned URL 의 서명에 {@code Content-MD5} 를 포함시켜 S3 가 본문 다이제스트와 비교하도록 한다.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "단일 파트 Presigned URL 발급 요청 (무결성 케이스). " +
            "클라이언트가 chunk 을 슬라이스 → MD5 계산 → 이 API 호출하는 플로우.")
    public static class PartPresignedUrlSingleRequest {

        @NotBlank(message = "key 는 필수입니다.")
        @Schema(description = "initiate 응답의 key 와 동일.",
                example = "files/pdf/3f2d04ec-12ab-4cde-9abc-1234567890ab__resume.pdf")
        private String key;

        @NotBlank(message = "uploadId 는 필수입니다.")
        @Schema(description = "initiate 응답의 uploadId 와 동일.", example = "upload-id-12345")
        private String uploadId;

        @NotNull(message = "partNumber 는 필수입니다.")
        @Min(value = 1, message = "partNumber 는 1 이상이어야 합니다.")
        @Max(value = 10000, message = "partNumber 는 10000 이하여야 합니다.")
        @Schema(description = "파트 번호. S3 multipart 규격상 1..10000.", example = "1")
        private Integer partNumber;

        @NotNull(message = "contentLength 는 필수입니다.")
        @Min(value = 1, message = "contentLength 는 1 이상이어야 합니다.")
        @Schema(description = "이 파트의 정확한 크기(byte). 서명에 결속된다 (DoS 방어).",
                example = "67108864")
        private Long contentLength;

        @Schema(description = "이 파트 본문의 MD5(Base64). 옵션 — 보내면 서명에 결속되고 S3 가 본문 다이제스트와 비교.",
                example = "8gWuEUu3Q5Qbd7Q+aXhP+w==")
        private String md5Base64;
    }

    /**
     * {@code POST /api/v1/files/multipart/part-presigned-url} 응답 본문.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "단일 파트 Presigned URL 발급 응답")
    public static class PartPresignedUrlSingleResponse {

        @Schema(description = "요청한 파트 번호.", example = "1")
        private Integer partNumber;

        @Schema(description = "S3 PUT presigned URL.")
        private String presignedUrl;

        @Schema(description = "이 URL 에 PUT 가능한 정확한 본문 크기(byte). 서명에 결속됨.")
        private Long contentLength;

        @Schema(description = "MD5 를 결속한 경우 이 값을 그대로 Content-MD5 헤더로 PUT 요청에 포함해야 한다.",
                example = "8gWuEUu3Q5Qbd7Q+aXhP+w==")
        private String md5Base64;
    }

    /**
     * {@code GET /api/v1/files/multipart/list-parts} 응답 본문.
     *
     * <p>네트워크 단절 후 클라이언트가 어디까지 업로드 했는지 확인 / 재개하기 위한 용도.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "현재까지 S3 가 받은 파트 목록 (재개용)")
    public static class ListPartsResponse {

        @Schema(description = "업로드 완료된 파트 목록. partNumber 정렬 보장 없음.")
        private List<UploadedPart> parts;

        /**
         * 업로드 완료된 한 개의 파트.
         */
        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @Schema(description = "업로드 완료된 한 개의 파트")
        public static class UploadedPart {

            @Schema(description = "파트 번호.", example = "1")
            private Integer partNumber;

            @Schema(description = "S3 가 보관하고 있는 해당 파트의 ETag.")
            private String etag;

            @Schema(description = "해당 파트의 실제 크기(byte).", example = "67108864")
            private Long size;
        }
    }

    /**
     * {@code POST /api/v1/files/multipart/complete} 요청 본문.
     *
     * <p>클라이언트가 모든 파트 PUT 을 마친 뒤 호출. 서버는 {@code parts} 를 {@code partNumber} 오름차순으로
     * 정렬해 S3 의 {@code CompleteMultipartUpload} 를 호출하고, 이후 {@code HeadObject} 로 실제 크기를 검증한다
     * (file_security.md §3.4 — 사후 검증). 검증 실패 시 {@code abortUpload} 가 호출된다.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "멀티파트 업로드 완료 요청")
    public static class CompleteMultipartUploadRequest {

        @NotBlank(message = "key 는 필수입니다.")
        @Schema(description = "initiate 응답의 key.")
        private String key;

        @NotBlank(message = "uploadId 는 필수입니다.")
        @Schema(description = "initiate 응답의 uploadId.")
        private String uploadId;

        @NotEmpty(message = "parts 는 비어있을 수 없습니다.")
        @Schema(description = "PUT 된 모든 파트의 (partNumber, ETag) 매핑. 정렬 순서 무관 — 서비스가 정렬.")
        private List<Part> parts;

        @NotBlank(message = "originalFileName 은 필수입니다.")
        @Schema(description = "표시용 원본 파일명. {@code File} 엔티티의 {@code filename} 으로 영속화.",
                example = "large_video.mp4")
        private String originalFileName;

        @NotNull(message = "fileSize 는 필수입니다.")
        @Min(value = 1, message = "fileSize 는 1 이상이어야 합니다.")
        @Schema(description = "클라이언트가 선언한 파일 크기(byte). 서버가 HeadObject 결과와 비교 검증.",
                example = "104857600")
        private Long fileSize;

        @Schema(description = "Content-Type. 누락 시 서버가 확장자에서 추론.", example = "video/mp4")
        private String mimeType;

        @Schema(description = "클라이언트가 지정한 문서 타입", example = "COVER_LETTER | PORTFOLIO")
        private DocumentType documentType;

        @Schema(description = "클라이언트가 지정한 액션 타입", example = "GENERATE | EDIT")
        private ActionType actionType;
    }

    /**
     * {@link CompleteMultipartUploadRequest} 내 파트 식별 정보.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Complete 요청에 포함되는 파트 정보 (S3 PUT 응답의 ETag)")
    public static class Part {

        @NotNull(message = "partNumber 는 필수입니다.")
        @Min(value = 1, message = "partNumber 는 1 이상이어야 합니다.")
        @Max(value = 10000, message = "partNumber 는 10000 이하여야 합니다.")
        @Schema(description = "1..10000 범위의 파트 번호.", example = "1")
        private Integer partNumber;

        @NotBlank(message = "etag 는 필수입니다.")
        @Schema(description = "S3 가 PUT 응답으로 돌려준 ETag(인용부호 포함 또는 미포함, 받은 그대로).",
                example = "\"etag-value\"")
        private String etag;
    }

    /**
     * {@code POST /api/v1/files/multipart/abort} 요청 본문.
     *
     * <p>클라이언트가 명시적으로 취소하거나, 서버가 사후 검증에서 실패를 감지했을 때 호출.
     * S3 가 중간 파트들을 정리한다 (file_security.md §7.2 — orphan cleanup).
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "멀티파트 업로드 취소 요청")
    public static class MultipartUploadAbortRequest {

        @NotBlank(message = "key 는 필수입니다.")
        @Schema(description = "initiate 응답의 key.")
        private String key;

        @NotBlank(message = "uploadId 는 필수입니다.")
        @Schema(description = "initiate 응답의 uploadId.")
        private String uploadId;
    }

    /**
     * {@code POST /api/v1/files/multipart/complete} 응답 본문.
     *
     * <p>업로드 검증을 모두 통과한 시점에 영속화된 {@link File} 메타데이터를 그대로 노출. {@code cdnUrl} 은
     * {@link FileUrlUtils#buildCdnUrl(String)} 로 도출 (S3 URL 직접 노출 차단 — file_security.md §5).
     * {@code status} 는 스캐닝 파이프라인이 없는 본 iteration 에서는 항상 {@code AVAILABLE}.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "업로드 완료 후 응답")
    public static class UploadFileResponse {

        @Schema(description = "영속화된 file row PK.", example = "12345")
        private Long fileId;

        @Schema(description = "표시용 원본 파일명.", example = "large_video.mp4")
        private String filename;

        @Schema(description = "클라이언트가 접근할 CDN URL ({@code cdn.base-url + s3ObjectKey}).",
                example = "https://cdn.passfolio.com/files/pdf/uuid__resume.pdf")
        private String cdnUrl;

        @Schema(description = "업로드된 파일의 실제 크기(byte). HeadObject 검증을 통과한 값.",
                example = "104857600")
        private Long fileSize;

        @Schema(description = "분류된 미디어 타입(IMAGE/VIDEO/AUDIO/PDF/UNKNOWN).", example = "VIDEO")
        private String mediaType;

        @Schema(description = "PENDING/SCANNING/AVAILABLE/INFECTED. 스캐닝 파이프라인이 없는 본 iteration 에서는 항상 AVAILABLE.",
                example = "AVAILABLE")
        private String status;

        @Schema(description = "클라이언트가 지정한 문서 타입. 미지정 시 null.", example = "COVER_LETTER")
        private String documentType;

        @Schema(description = "클라이언트가 지정한 액션 타입. 미지정 시 null.", example = "GENERATE")
        private String actionType;

        /**
         * 영속화된 {@link File} 엔티티에서 응답 DTO 를 생성한다.
         *
         * <p>{@code cdnUrl} 은 {@link FileUrlUtils#buildCdnUrl(String)} 으로 도출되며,
         * {@code cdn.base-url} 미설정 시 {@code IllegalStateException} 이 전파된다 (fail-fast — 보안 정책).
         *
         * @param file 검증을 모두 통과해 영속화된 파일 엔티티. {@code null} 불가.
         * @return 클라이언트 노출용 응답 DTO.
         */
        public static UploadFileResponse of(File file) {
            return UploadFileResponse.builder()
                    .fileId(file.getId())
                    .filename(file.getFilename())
                    .cdnUrl(FileUrlUtils.buildCdnUrl(file.getS3ObjectKey()))
                    .fileSize(file.getFileSize())
                    .mediaType(file.getMediaType().name())
                    .status("AVAILABLE")
                    .documentType(file.getDocumentType() != null ? file.getDocumentType().name() : null)
                    .actionType(file.getActionType() != null ? file.getActionType().name() : null)
                    .build();
        }
    }

    /**
     * {@code GET /api/v1/files/me} 응답 본문.
     *
     * <p>현재 인증된 사용자가 업로드(즉, {@link File#getCreatedBy()} 가 본인의 userId 와 일치)한 모든 파일의
     * CDN URL 목록. 각 URL 은 {@link FileUrlUtils#buildCdnUrl(String)} 로 도출된다.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "현재 사용자가 업로드한 파일들의 CDN URL 목록 응답")
    public static class MyFileCdnUrlsResponse {

        @Schema(description = "사용자가 업로드한 파일들의 CDN URL 목록 (최신 업로드 순).",
                example = "[\"https://cdn.passfolio.com/files/pdf/uuid__resume.pdf\"]")
        private List<String> cdnUrls;
    }
}
