package com.capstone.passfolio.domain.s3.dto;

import com.capstone.passfolio.domain.file.entity.enums.MediaType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;

/**
 * {@code S3Service} 의 입출력 DTO 모음 (외부 노출 X — 같은 도메인 모듈 내 호출자만 사용).
 *
 * <p>클라이언트에 노출되는 REST DTO 는 {@code com.capstone.passfolio.domain.file.dto.FileDto} 에 별도로 정의한다.
 * 즉 본 클래스는 "S3 SDK 호출 파라미터 ↔ 서비스 시그니처" 사이를 잇는 내부 carrier 일 뿐이며,
 * Jackson 직렬화 / Bean Validation 대상이 아니다 (검증은 controller 레이어의 FileDto 에서 끝낸다).
 *
 * <p>모든 nested 클래스는 {@code @Data @Builder @NoArgsConstructor @AllArgsConstructor} 패턴으로 통일한다 —
 * 프로젝트의 다른 DTO carrier ({@code UserDto}, {@code GitHubDto}, {@code JwtDto}) 과 일치시켜 IDE 자동완성/리팩터링이
 * 동일한 형태로 동작하도록 하기 위함.
 *
 * <p>설계 참조: {@code 0001-harness-design.md} §"Files plan / S3ServiceDto" 와 §"Interface contracts".
 *
 * @see com.capstone.passfolio.domain.file.entity.enums.MediaType
 */
public class S3ServiceDto {

    private S3ServiceDto() {
        // 정적 컨테이너. 인스턴스화 방지.
    }

    /**
     * Multipart 업로드 시작 요청. {@code FileService.initiateMultipartUpload} 가
     * controller 로부터 받은 {@code FileDto.MultipartUploadInitiateRequest} 를 검증/정규화 한 뒤 본 DTO 로 변환해
     * {@code S3Service.initiateUpload} 에 전달한다.
     *
     * <p>{@code mediaType} 은 MIME prefix 또는 확장자 분류 결과로, S3 객체 키의 subfolder 결정
     * (예: {@code IMAGE} → {@code files/images/}, {@code PDF} → {@code files/pdf/}, {@code VIDEO}/{@code AUDIO} → {@code files/videos/}) 에 사용된다.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadInitiateRequest {
        /** 클라이언트가 전달한 원본 파일명(표시용). 보안상 이 값을 그대로 S3 key 에 사용하지 않는다. */
        private String originalFileName;
        /** 클라이언트가 선언한 MIME 타입 (예: {@code application/pdf}). 신뢰하지 않고 검증 후 사용. */
        private String mimeType;
        /** 업로드 예정 파일 크기 (bytes). 정책 (≤ 100MB) 검증 및 part 분할 계산에 사용. */
        private Long fileSize;
        /** 분류된 미디어 카테고리. {@link MediaType} 참조. */
        private MediaType mediaType;
    }

    /**
     * Multipart 업로드 시작 응답.
     *
     * <p>여기서 확정된 {@code key} 는 클라이언트가 임의로 변경할 수 없다 (서버 권위) — file_security.md §3.2 참조.
     * {@code uploadId} 는 후속 part 업로드 / complete / abort 호출의 식별자.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadInitiateResponse {
        /** 서버가 확정한 S3 객체 키 ({@code {subfolder}/{UUID}__{sanitized_filename}} 형식). */
        private String key;
        /** AWS S3 가 발급한 multipart upload 식별자. */
        private String uploadId;
        /** S3 가 인지하는 Content-Type ({@code mimeType} 정규화 결과). */
        private String contentType;
    }

    /**
     * 단일 파트의 presigned PUT URL 발급 요청.
     *
     * <p>{@code contentLength} 는 항상 서명에 바인딩되어 클라이언트가 더 큰 청크를 업로드할 수 없도록 막는다
     * (file_security.md §3.4, DoS 방어). {@code contentMd5Base64} 는 선택 — 클라이언트가 무결성 보장을 원할 때만
     * 사용 (file_security.md §3.1).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignedUrlRequest {
        /** {@link UploadInitiateResponse#getKey()} 와 동일한 S3 객체 키. */
        private String key;
        /** {@link UploadInitiateResponse#getUploadId()} 와 동일한 multipart upload 식별자. */
        private String uploadId;
        /** 파트 번호 (1..10000). S3 multipart 규격 한계. */
        private Integer partNumber;
        /** 이 파트의 정확한 크기 (bytes). 서명에 포함되어 보안 강화. */
        private Long contentLength;
        /** 이 파트 본문의 MD5 해시 (Base64). null 이면 MD5 바인딩 생략. */
        private String contentMd5Base64;
    }

    /**
     * 한 multipart 업로드의 모든 파트 presigned URL 일괄 발급 요청.
     *
     * <p>{@code S3Service.generatePartPresignedUrls} 에 전달되어 {@code calculatePartCount(fileSize)} 개의
     * presigned URL 을 한 번에 만든다. 클라이언트는 받은 URL 들을 병렬 PUT 으로 사용한다.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartPresignedUrlRequest {
        /** {@link UploadInitiateResponse#getKey()} 와 동일한 S3 객체 키. */
        private String key;
        /** {@link UploadInitiateResponse#getUploadId()} 와 동일한 multipart upload 식별자. */
        private String uploadId;
        /** 발급되는 모든 presigned URL 의 만료시간. {@code FileUploadProperties.presignedUrlTtlMinutes} 에서 환산. */
        private Duration duration;
        /** 전체 업로드 파일 크기 (bytes). 파트 개수/크기 계산의 입력. */
        private Long fileSize;
    }

    /**
     * 단일 파트의 presigned URL 발급 결과.
     *
     * <p>응답은 {@code FileService} 를 거쳐 클라이언트에게 전달되며, 클라이언트는 {@code presignedUrl}
     * 에 정확히 {@code contentLength} 바이트의 본문으로 PUT 해야 서명이 맞는다.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartPresignedUrlResponse {
        /** 1..10000 범위의 파트 번호. */
        private int partNumber;
        /** AWS S3 presigned PUT URL. */
        private String presignedUrl;
        /** 이 URL 에 PUT 가능한 정확한 본문 크기 (bytes). 서명에 바인딩된 값. */
        private Long contentLength;
    }

    /**
     * Multipart 업로드 완료 요청.
     *
     * <p>클라이언트가 모든 파트 PUT 을 마친 뒤 {@code FileService.completeMultipartUpload} 를 호출하면,
     * 서비스는 이 DTO 로 변환해 {@code S3Service.completeUpload} 에 전달한다.
     * {@code S3Service.completeUpload} 는 {@code parts} 를 {@code partNumber} 오름차순으로 정렬한 후
     * S3 의 {@code CompleteMultipartUpload} 를 호출한다 (S3 가 정렬 순서를 강제하기 때문).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteUploadRequest {
        /** {@link UploadInitiateResponse#getKey()} 와 동일한 S3 객체 키. */
        private String key;
        /** {@link UploadInitiateResponse#getUploadId()} 와 동일한 multipart upload 식별자. */
        private String uploadId;
        /** 클라이언트가 PUT 후 받은 ETag 와 매칭되는 파트 목록. 정렬 순서 무관 — 서비스가 정렬한다. */
        private List<Part> parts;

        /**
         * 완료 요청에 포함되는 단일 파트의 ETag 매핑.
         *
         * <p>S3 는 각 파트 PUT 응답 헤더로 ETag 를 돌려주며, complete 호출 시 (partNumber, ETag) 쌍 목록이 필요하다.
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Part {
            /** 1..10000 범위의 파트 번호. */
            private Integer partNumber;
            /** S3 가 PUT 응답으로 돌려준 ETag (인용부호 포함 또는 미포함, 클라이언트가 받은 그대로). */
            private String etag;
        }
    }

    /**
     * Multipart 업로드 중단 요청.
     *
     * <p>클라이언트가 명시적으로 abort 하거나, 서버가 post-upload 검증 (예: 크기 불일치) 에서 실패를 감지했을 때
     * 호출되어 S3 가 중간 파트들을 정리하도록 한다 — file_security.md §7.2 (orphan cleanup) 참조.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AbortUploadRequest {
        /** {@link UploadInitiateResponse#getKey()} 와 동일한 S3 객체 키. */
        private String key;
        /** {@link UploadInitiateResponse#getUploadId()} 와 동일한 multipart upload 식별자. */
        private String uploadId;
    }

    /**
     * 서버측에서 본 업로드 진행 상황 응답.
     *
     * <p>{@code S3Service.listUploadedParts} 는 S3 의 {@code ListParts} API 를 호출해
     * 현재까지 업로드된 파트들을 반환한다. 클라이언트가 네트워크 단절 후 재개할 때 어디까지 올렸는지 확인하는 용도.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListPartsResponse {
        /** 업로드 완료된 파트 목록. {@code partNumber} 오름차순으로 보장하지 않음 — 호출자가 필요하면 정렬할 것. */
        private List<UploadedPart> parts;

        /**
         * 한 개의 업로드 완료 파트 정보.
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class UploadedPart {
            /** 1..10000 범위의 파트 번호. */
            private Integer partNumber;
            /** S3 가 보관하고 있는 해당 파트의 ETag. */
            private String etag;
            /** 해당 파트의 실제 크기 (bytes). */
            private Long size;
        }
    }
}
