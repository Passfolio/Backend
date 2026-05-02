package com.capstone.passfolio.domain.file.service;

import com.capstone.passfolio.domain.file.dto.FileDto;
import com.capstone.passfolio.domain.file.entity.File;
import com.capstone.passfolio.domain.file.entity.enums.MediaType;
import com.capstone.passfolio.domain.file.repository.FileRepository;
import com.capstone.passfolio.domain.s3.dto.S3ServiceDto;
import com.capstone.passfolio.domain.s3.service.S3Service;
import com.capstone.passfolio.system.config.file.FileUploadProperties;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;

import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * 컨트롤러 ↔ {@link S3Service} 사이의 비즈니스 로직 계층.
 *
 * <p>책임:
 * <ul>
 *   <li>업로드 정책 (확장자 화이트리스트 + 100MB 한도) 사전 검증 — initiate 단계의 cheap reject
 *       ({@link FileUploadProperties}, file_security.md §3.4)</li>
 *   <li>{@link S3Service} 위임 (S3 SDK 실호출은 본 클래스가 직접 하지 않는다)</li>
 *   <li>complete 단계에서 사후 크기 검증 ({@code HeadObject}) → 약속한 fileSize 와 다르면
 *       {@code abortUpload} + {@link ErrorCode#FILE_SIZE_MISMATCH}</li>
 *   <li>{@link File} 엔티티 영속화 ({@link FileRepository})</li>
 *   <li>IDOR 방어용 {@link #validateFileOwner} / {@link #validateFileOwners} — 향후 cross-domain
 *       attachment 가 fileId 를 받을 때 호출 (file_security.md §3.3)</li>
 * </ul>
 *
 * <p>트랜잭션 주의: complete 단계는 S3 호출 + DB 저장이 한 트랜잭션 안에 있다. S3 호출 성공 후
 * DB 가 실패하면 S3 에 객체가 남는다 — file_security.md §7.2 (orphan multipart cleanup) 의
 * lifecycle rule 또는 별도 정리 스케줄러가 보조해야 한다 (본 백로그 범위 외).
 *
 * <p>설계 참조: {@code 0001-harness-design.md} §"Files plan / FileService" + §"Interface contracts".
 *
 * @see S3Service
 * @see FileUploadProperties
 * @see FileRepository
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FileService {

    private final S3Service s3Service;
    private final FileRepository fileRepository;
    private final FileUploadProperties uploadProperties;

    // ============================================================
    // 1) Multipart 초기화 — uploadId + 모든 part presigned URL 발급
    // ============================================================

    /**
     * 멀티파트 업로드 세션을 시작한다.
     *
     * <p>흐름:
     * <ol>
     *   <li>{@link #validateUploadPolicy} — 확장자 화이트리스트 + 100MB 한도 사전 거부</li>
     *   <li>{@link S3Service#initiateUpload} — S3 가 발급한 {@code uploadId} + 서버 권위로 확정한 {@code key}</li>
     *   <li>{@link S3Service#calculatePartCount} 만큼 일괄 presigned URL 발급
     *       ({@link S3Service#generatePartPresignedUrls})</li>
     * </ol>
     *
     * <p>{@code mediaType} 분류는 controller 입력 ({@code mimeType} + {@code originalFileName}) 으로부터
     * 본 메서드가 수행해 {@link S3ServiceDto.UploadInitiateRequest#getMediaType()} 으로 전달한다 — S3Service
     * 가 subfolder 결정에 사용 ({@code IMAGE → files/images/}, {@code PDF → files/pdf/}, {@code VIDEO/AUDIO → files/videos/}).
     *
     * @param request 클라이언트 입력 (originalFileName, mimeType?, fileSize). 모두 controller 의
     *                {@code @Valid} 가 1차 검증한 상태.
     * @return 클라이언트가 즉시 PUT 을 시작할 수 있는 모든 part presigned URL + 식별자
     * @throws RestException {@link ErrorCode#FILE_INVALID_SIZE},
     *                       {@link ErrorCode#FILE_SIZE_EXCEEDED},
     *                       {@link ErrorCode#FILE_EXTENSION_NOT_ALLOWED}
     */
    public FileDto.MultipartUploadInitiateResponse initiateMultipartUpload(
            FileDto.MultipartUploadInitiateRequest request) {

        // ---- 정책 검증: 확장자 + 크기 ----
        validateUploadPolicy(request.getOriginalFileName(), request.getFileSize());

        MediaType mediaType = determineMediaType(request.getMimeType(), request.getOriginalFileName());

        S3ServiceDto.UploadInitiateResponse s3Response = s3Service.initiateUpload(
                S3ServiceDto.UploadInitiateRequest.builder()
                        .originalFileName(request.getOriginalFileName())
                        .mimeType(request.getMimeType())
                        .fileSize(request.getFileSize())
                        .mediaType(mediaType)
                        .build());

        Integer partCount = s3Service.calculatePartCount(request.getFileSize());

        List<S3ServiceDto.PartPresignedUrlResponse> partPresignedUrls =
                s3Service.generatePartPresignedUrls(S3ServiceDto.PartPresignedUrlRequest.builder()
                        .key(s3Response.getKey())
                        .uploadId(s3Response.getUploadId())
                        .fileSize(request.getFileSize())
                        .build());

        List<FileDto.PartPresignedUrl> dtoList = partPresignedUrls.stream()
                .map(p -> FileDto.PartPresignedUrl.builder()
                        .partNumber(p.getPartNumber())
                        .presignedUrl(p.getPresignedUrl())
                        .contentLength(p.getContentLength())
                        .build())
                .toList();

        return FileDto.MultipartUploadInitiateResponse.builder()
                .key(s3Response.getKey())
                .uploadId(s3Response.getUploadId())
                .contentType(s3Response.getContentType())
                .partCount(partCount)
                .partPresignedUrls(dtoList)
                .build();
    }

    // ============================================================
    // 2) 단일 part Presigned URL 발급 — Content-MD5 결속 흐름
    // ============================================================

    /**
     * 클라이언트가 chunk 을 슬라이스 → MD5 계산 → 본 API 호출 → URL 받아서 PUT 하는 무결성 결속용.
     *
     * <p>{@code md5Base64} 가 비어있지 않으면 {@link S3Service#generatePresignedUrl} 에 그대로 전달되어
     * 서명에 {@code Content-MD5} 가 결속된다 (file_security.md §3.1). null/blank 면 결속 생략 — 단,
     * {@code contentLength} 는 항상 결속된다 (file_security.md §3.4, DoS 방어).
     *
     * @param request key/uploadId/partNumber/contentLength + (옵션) md5Base64. 모두 {@code @Valid} 가 검증.
     * @return presigned PUT URL + 본문 정확 크기 + (있다면) MD5 (클라이언트가 그대로 PUT 헤더로 전달)
     */
    @Transactional(readOnly = true)
    public FileDto.PartPresignedUrlSingleResponse generatePartPresignedUrl(
            FileDto.PartPresignedUrlSingleRequest request) {

        URL url = s3Service.generatePresignedUrl(S3ServiceDto.PresignedUrlRequest.builder()
                .key(request.getKey())
                .uploadId(request.getUploadId())
                .partNumber(request.getPartNumber())
                .contentLength(request.getContentLength())
                .contentMd5Base64(request.getMd5Base64())
                .build());

        return FileDto.PartPresignedUrlSingleResponse.builder()
                .partNumber(request.getPartNumber())
                .presignedUrl(url.toString())
                .contentLength(request.getContentLength())
                .md5Base64(request.getMd5Base64())
                .build();
    }

    // ============================================================
    // 3) 재개(resume) — S3 에 도착한 part 조회
    // ============================================================

    /**
     * 네트워크 단절 등으로 클라이언트가 어디까지 PUT 했는지 확인 / 재개하기 위해 호출.
     *
     * <p>{@link S3Service#listUploadedParts} 의 결과를 클라이언트 노출 DTO 로 1:1 매핑한다.
     * 정렬 보장은 없으며, 필요한 경우 클라이언트가 정렬한다.
     *
     * @param key      initiate 응답의 S3 객체 키
     * @param uploadId initiate 응답의 multipart upload 식별자
     * @return 업로드 완료된 파트 목록
     */
    @Transactional(readOnly = true)
    public FileDto.ListPartsResponse listUploadedParts(String key, String uploadId) {
        S3ServiceDto.ListPartsResponse s3Response = s3Service.listUploadedParts(key, uploadId);
        List<FileDto.ListPartsResponse.UploadedPart> parts = s3Response.getParts().stream()
                .map(p -> FileDto.ListPartsResponse.UploadedPart.builder()
                        .partNumber(p.getPartNumber())
                        .etag(p.getEtag())
                        .size(p.getSize())
                        .build())
                .toList();
        return FileDto.ListPartsResponse.builder().parts(parts).build();
    }

    // ============================================================
    // 4) Multipart 완료 — S3 part 병합 + 사후 크기 검증 + File 엔티티 영속화
    // ============================================================

    /**
     * 모든 part PUT 이 끝난 뒤 호출되어 S3 commit + 사후 검증 + DB 저장을 한 트랜잭션 안에서 수행한다.
     *
     * <p>흐름:
     * <ol>
     *   <li>{@link S3Service#completeUpload} — S3 가 부분들을 단일 객체로 병합</li>
     *   <li>{@link S3Service#getObjectActualSize} ({@code HeadObject}) — 실제 크기 조회</li>
     *   <li>실제 크기 ≠ 약속 크기 → {@link S3Service#abortUpload} 후 {@link ErrorCode#FILE_SIZE_MISMATCH}</li>
     *   <li>{@link MediaType} 분류 → {@link File} 엔티티 빌드 → {@link FileRepository#save}</li>
     * </ol>
     *
     * <p>크기 검증을 통과한 시점의 actual size 가 DB 에 영속화된다 — 클라이언트가 보낸 fileSize 가 아니라
     * S3 가 보고한 값이 신뢰할 수 있는 source of truth 이기 때문 (file_security.md §3.4).
     *
     * @param request key/uploadId/parts/originalFileName/fileSize/mimeType
     * @return 영속화된 {@link File} 엔티티. 컨트롤러가 {@link FileDto.UploadFileResponse#of(File)} 로 변환.
     * @throws RestException {@link ErrorCode#FILE_SIZE_MISMATCH} (실제 크기 불일치 시)
     */
    public File completeMultipartUpload(FileDto.CompleteMultipartUploadRequest request) {

        S3ServiceDto.CompleteUploadRequest s3Request = S3ServiceDto.CompleteUploadRequest.builder()
                .key(request.getKey())
                .uploadId(request.getUploadId())
                .parts(request.getParts().stream()
                        .map(p -> S3ServiceDto.CompleteUploadRequest.Part.builder()
                                .partNumber(p.getPartNumber())
                                .etag(p.getEtag())
                                .build())
                        .toList())
                .build();

        CompleteMultipartUploadResponse s3Response = s3Service.completeUpload(s3Request);
        log.info("[FileService] S3 complete done. location={}, etag={}",
                s3Response.location(), s3Response.eTag());

        // 사후 크기 검증 — 약속한 fileSize 와 실제 객체 크기 비교 (file_security.md §3.4).
        // 어긋나면 약속을 깬 클라이언트로 간주 → S3 객체 정리 + DB 저장 취소.
        long actualSize = s3Service.getObjectActualSize(request.getKey());
        if (actualSize != request.getFileSize()) {
            log.warn("[FileService] Size mismatch. promised={}, actual={}, key={}. Aborting.",
                    request.getFileSize(), actualSize, request.getKey());
            s3Service.abortUpload(S3ServiceDto.AbortUploadRequest.builder()
                    .key(request.getKey())
                    .uploadId(request.getUploadId())
                    .build());
            throw new RestException(ErrorCode.FILE_SIZE_MISMATCH);
        }

        MediaType mediaType = determineMediaType(request.getMimeType(), request.getOriginalFileName());

        File file = File.builder()
                .s3ObjectKey(request.getKey())
                .filename(request.getOriginalFileName())
                .fileSize(actualSize)
                .mediaType(mediaType)
                .build();

        return fileRepository.save(file);
    }

    // ============================================================
    // 5) Multipart 중단
    // ============================================================

    /**
     * 클라이언트의 명시적 abort. {@link S3Service#abortUpload} 에 위임 — orphan part 정리
     * (file_security.md §7.2).
     *
     * @param key      initiate 응답의 S3 객체 키
     * @param uploadId initiate 응답의 multipart upload 식별자
     */
    public void abortMultipartUpload(String key, String uploadId) {
        s3Service.abortUpload(S3ServiceDto.AbortUploadRequest.builder()
                .key(key)
                .uploadId(uploadId)
                .build());
    }

    // ============================================================
    // 정책 검증 (private)
    // ============================================================

    /**
     * 업로드 정책 사전 검증 — initiate 단계의 cheap reject.
     *
     * <p>검증 순서 (실패 시 즉시 throw):
     * <ol>
     *   <li>fileSize null/0 이하 → {@link ErrorCode#FILE_INVALID_SIZE} (HTTP 400)</li>
     *   <li>fileSize > {@link FileUploadProperties#getMaxFileSizeBytes} → {@link ErrorCode#FILE_SIZE_EXCEEDED}
     *       (HTTP 413)</li>
     *   <li>{@code !uploadProperties.isExtensionAllowed(originalFileName)} →
     *       {@link ErrorCode#FILE_EXTENSION_NOT_ALLOWED} (HTTP 400)</li>
     * </ol>
     *
     * <p>크기 검증을 확장자 검증보다 먼저 두는 이유: HTTP 413 (Payload Too Large) 가 의미상
     * 더 명시적인 거부이기 때문. 단, 두 위반이 동시일 수 없는 입력 (예: {@code .exe} + 50MB) 에서는
     * 어느 쪽이든 거부 결과가 동일하므로 순서가 외부 관찰에 영향을 주지는 않는다.
     */
    private void validateUploadPolicy(String originalFileName, Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            throw new RestException(ErrorCode.FILE_INVALID_SIZE);
        }
        if (fileSize > uploadProperties.getMaxFileSizeBytes()) {
            log.warn("[FileService] fileSize exceeds limit. requested={}, limit={}",
                    fileSize, uploadProperties.getMaxFileSizeBytes());
            throw new RestException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
        if (!uploadProperties.isExtensionAllowed(originalFileName)) {
            log.warn("[FileService] extension not allowed. filename={}", originalFileName);
            throw new RestException(ErrorCode.FILE_EXTENSION_NOT_ALLOWED);
        }
    }

    // ============================================================
    // IDOR 가드 (cross-domain attachment 용 public helper)
    // ============================================================

    /**
     * 다른 도메인 (예: Resume / Project 첨부) 이 fileId 를 받아 자신의 엔티티에 첨부할 때 호출.
     * createdBy 와 요청 user 가 일치하는지 확인해 IDOR 공격 (다른 사용자 파일 무단 첨부) 을 차단한다.
     *
     * <p>본 백로그 (initiate / part / complete / abort 5개 endpoint) 자체에서는 호출하지 않으며,
     * 향후 attachment 도메인이 도입될 때 사용된다. 단위 테스트 (T18) 가 동작을 명세화한다.
     *
     * @param fileId 검증 대상 파일의 PK
     * @param userId 현재 인증된 사용자의 PK
     * @return 소유권이 확인된 {@link File} 엔티티
     * @throws RestException {@link ErrorCode#FILE_NOT_FOUND} (해당 fileId 없음),
     *                       {@link ErrorCode#AUTH_FORBIDDEN} (소유자 불일치)
     */
    @Transactional(readOnly = true)
    public File validateFileOwner(Long fileId, Long userId) {
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RestException(ErrorCode.FILE_NOT_FOUND));

        if (!file.getCreatedBy().equals(userId)) {
            log.warn("IDOR Attempt: fileId={}, requestUser={}, owner={}",
                    fileId, userId, file.getCreatedBy());
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
        return file;
    }

    /**
     * {@link #validateFileOwner} 의 일괄 버전. 첨부 N개를 한 번에 검증할 때 사용.
     *
     * <p>중복된 fileId 는 distinct 카운트로 정규화되어 요청 vs 조회 결과의 cardinality 가 비교된다 —
     * 존재하지 않는 fileId 가 섞이면 일괄 실패시킨다. 부분 성공을 허용하지 않는 이유: 첨부 트랜잭션
     * 자체가 all-or-nothing 시맨틱을 요구하기 때문 (반쯤 첨부된 상태가 유저 가시성 측면에서 더 위험).
     *
     * @param fileIds 검증 대상 파일들의 PK. {@code null}/빈 리스트 → 빈 리스트 반환
     * @param userId  현재 인증된 사용자의 PK
     * @return 소유권이 확인된 {@link File} 엔티티 리스트
     * @throws RestException {@link ErrorCode#FILE_NOT_FOUND} (어느 하나라도 미존재),
     *                       {@link ErrorCode#AUTH_FORBIDDEN} (어느 하나라도 다른 소유자)
     */
    @Transactional(readOnly = true)
    public List<File> validateFileOwners(List<Long> fileIds, Long userId) {
        if (fileIds == null || fileIds.isEmpty()) return Collections.emptyList();

        List<File> files = fileRepository.findAllById(fileIds);
        if (files.size() != fileIds.stream().distinct().count()) {
            throw new RestException(ErrorCode.FILE_NOT_FOUND);
        }

        boolean isAllMine = files.stream().allMatch(f -> f.getCreatedBy().equals(userId));
        if (!isAllMine) {
            log.warn("IDOR Attempt (bulk): user={}", userId);
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
        return files;
    }

    // ============================================================
    // Helpers (private)
    // ============================================================

    /**
     * MIME prefix / 파일명 확장자로부터 {@link MediaType} 분류.
     *
     * <p>분류 우선순위:
     * <ol>
     *   <li>MIME prefix → {@code image/}, {@code video/}, {@code audio/}</li>
     *   <li>{@code application/pdf} 또는 {@code .pdf} 확장자 → {@link MediaType#PDF}</li>
     *   <li>확장자 매칭 (whitelist 와 동일한 set 의 부분집합)</li>
     *   <li>아무것도 매칭 안되면 {@link MediaType#UNKNOWN}</li>
     * </ol>
     *
     * <p>화이트리스트 ({@link FileUploadProperties}) 가 사전 차단하므로 일반 흐름상 {@code UNKNOWN} 이
     * 영속화될 일은 없으며, 안전한 fallback 으로만 존재한다.
     */
    private MediaType determineMediaType(String mimeType, String fileName) {
        if (mimeType == null && fileName == null) return MediaType.UNKNOWN;
        String type = mimeType != null ? mimeType.toLowerCase() : "";
        String name = fileName != null ? fileName.toLowerCase() : "";

        if (type.startsWith("image/") || name.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|svg)$")) {
            return MediaType.IMAGE;
        }
        if (type.startsWith("video/") || name.matches(".*\\.(mp4|avi|mov|wmv|flv|webm|mkv)$")) {
            return MediaType.VIDEO;
        }
        if (type.startsWith("audio/") || name.matches(".*\\.(mp3|wav|flac|aac|ogg)$")) {
            return MediaType.AUDIO;
        }
        if (type.equals("application/pdf") || name.endsWith(".pdf")) {
            return MediaType.PDF;
        }
        return MediaType.UNKNOWN;
    }
}
