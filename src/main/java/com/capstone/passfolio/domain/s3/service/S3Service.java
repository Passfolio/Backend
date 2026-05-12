package com.capstone.passfolio.domain.s3.service;

import com.capstone.passfolio.domain.file.entity.enums.MediaType;
import com.capstone.passfolio.domain.s3.dto.S3ServiceDto;
import com.capstone.passfolio.system.config.file.FileUploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.StorageClass;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * AWS S3 multipart 업로드 lifecycle 의 SDK 호출 전용 어댑터.
 *
 * <p>비즈니스 정책 (확장자 화이트리스트 / 100MB 한도 / {@code File} 엔티티 영속화 / 사후 크기 검증 후 abort) 은
 * {@code FileService} 가 담당하고, 본 클래스는 다음 5단계의 S3 호출만 책임진다:
 * <ol>
 *   <li>{@link #initiateUpload(S3ServiceDto.UploadInitiateRequest)} — {@code CreateMultipartUpload} 호출,
 *       서버 권위로 {@code key} 확정 (UUID + sanitized filename)</li>
 *   <li>{@link #generatePartPresignedUrls(S3ServiceDto.PartPresignedUrlRequest)} — {@link #calculatePartCount(Long)}
 *       만큼 part 별 presigned PUT URL 일괄 발급 ({@code Content-Length} 결속)</li>
 *   <li>{@link #generatePresignedUrl(S3ServiceDto.PresignedUrlRequest)} — 단일 part presigned URL
 *       ({@code Content-Length} + 옵션 {@code Content-MD5} 결속) — 재발급/재개 시나리오</li>
 *   <li>{@link #completeUpload(S3ServiceDto.CompleteUploadRequest)} — part 목록을 {@code partNumber} 오름차순
 *       정렬 후 {@code CompleteMultipartUpload} 호출 (S3 가 정렬 강제)</li>
 *   <li>{@link #abortUpload(S3ServiceDto.AbortUploadRequest)} — orphan 파트 정리
 *       (file_security.md §7.2)</li>
 * </ol>
 * 보조: {@link #listUploadedParts(String, String)} — 재개 지원, {@link #getObjectActualSize(String)} — 사후 크기 검증.
 *
 * <p>보안 결속 요약:
 * <ul>
 *   <li>{@code Content-Length} 항상 서명에 포함 — 약속한 part 크기 외엔 PUT 거부 (DoS 방어, file_security.md §3.4)</li>
 *   <li>{@code Content-MD5} 클라이언트가 보낼 때만 서명에 포함 — 본문 변조 거부 (무결성, §3.1)</li>
 *   <li>{@code Content-Type} {@code CreateMultipartUpload} 단계에서 세션에 고정 (MIME 스푸핑 1차 방어)</li>
 *   <li>S3 객체 키는 서버가 {@code UUID + sanitized filename} 으로 확정 — 클라이언트 경로 trusts X (§3.2)</li>
 * </ul>
 *
 * <p>설계 참조: {@code 0001-harness-design.md} §"Files plan / S3Service" 와 §"Interface contracts".
 *
 * @see com.capstone.passfolio.domain.s3.dto.S3ServiceDto
 * @see com.capstone.passfolio.system.config.file.FileUploadProperties
 * @see com.capstone.passfolio.system.config.aws.S3PresignerConfig
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final FileUploadProperties uploadProperties;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    // ---------- Part 분할 상수 (AWS S3 multipart 규격) ----------

    /** AWS S3 multipart 최소 part 크기 (마지막 part 제외). */
    private static final long MIN_PART_SIZE = 5L * 1024 * 1024;            // 5 MB

    /** part 당 최대 크기. AWS 한도(5GB)보다 안전하게 2GB 로 제한. */
    private static final long MAX_PART_SIZE = 2L * 1024 * 1024 * 1024L;    // 2 GB

    /** AWS S3 multipart 최대 part 개수. */
    private static final long MAX_PART_COUNT = 10_000L;

    /** 파일이 1GB 미만일 때 권장 part 크기. */
    private static final long RECOMMENDED_PART_SIZE_SMALL = 64L * 1024 * 1024;   // 64 MB

    /** 파일이 1GB 이상일 때 권장 part 크기. */
    private static final long RECOMMENDED_PART_SIZE_LARGE = 128L * 1024 * 1024;  // 128 MB

    /** 권장 part 크기 결정 임계점. */
    private static final long LARGE_FILE_THRESHOLD = 1024L * 1024 * 1024L;       // 1 GB

    // ============================================================
    // 1) Multipart Upload 초기화
    // ============================================================

    /**
     * S3 에 multipart upload 세션을 시작한다.
     *
     * <p>키는 서버가 {@code files/{videos|images|pdf|other}/{UUID}__{sanitized_filename}} 형식으로 확정한다 — 클라이언트가
     * 보낸 {@code originalFileName} 은 sanitization 후 표시용으로만 키에 포함되며, 경로 traversal 또는
     * 동일 이름 충돌은 모두 무력화된다 (file_security.md §3.2).
     *
     * <p>{@code Content-Type} 은 {@code CreateMultipartUpload} 호출 시점에 세션에 고정되므로 part 별
     * presigned URL 에서는 별도로 결속할 필요가 없다.
     *
     * @param request {@code originalFileName}, {@code mimeType}, {@code mediaType} 등 정규화된 입력
     * @return S3 가 발급한 {@code uploadId} + 서버가 확정한 {@code key} + 정규화된 {@code contentType}
     */
    public S3ServiceDto.UploadInitiateResponse initiateUpload(S3ServiceDto.UploadInitiateRequest request) {
        String subFolder = determineSubFolder(request.getMediaType(), request.getMimeType(), request.getOriginalFileName());
        String key = generateKey(subFolder, request.getOriginalFileName());
        String contentType = guessContentType(request.getMimeType(), request.getOriginalFileName());

        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .storageClass(StorageClass.STANDARD)
                .build();

        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(createRequest);
        log.info("[S3] multipart initiated key={}, uploadId={}", key, response.uploadId());

        return S3ServiceDto.UploadInitiateResponse.builder()
                .uploadId(response.uploadId())
                .key(key)
                .contentType(contentType)
                .build();
    }

    // ============================================================
    // 2-a) 단일 Part Presigned URL — Content-Length + (옵션) Content-MD5 결속
    // ============================================================

    /**
     * 단일 part 에 대한 presigned PUT URL 을 발급한다.
     *
     * <p>{@code contentLength} 가 양수이면 서명에 결속 — 서버가 약속한 청크보다 큰 PUT 은 S3 가
     * {@code SignatureDoesNotMatch} 로 거부한다 (file_security.md §3.4).
     *
     * <p>{@code contentMd5Base64} 가 비어있지 않으면 서명에 결속 — 본문이 다르면 S3 가 {@code BadDigest}
     * 로 거부한다 (file_security.md §3.1). 클라이언트가 무결성 보장을 원할 때만 보내고, 단순 클라이언트는
     * 생략 가능 — design Decision "Content-MD5 optional" 참조.
     *
     * @param request {@code key}/{@code uploadId}/{@code partNumber}/{@code contentLength}/{@code contentMd5Base64}
     * @return presigned PUT URL (만료 = {@code FileUploadProperties.presignedUrlTtlMinutes}, 기본 10분)
     */
    public URL generatePresignedUrl(S3ServiceDto.PresignedUrlRequest request) {
        var uploadPartReqBuilder = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(request.getKey())
                .uploadId(request.getUploadId())
                .partNumber(request.getPartNumber());

        // contentLength 결속 (DoS 방어)
        if (request.getContentLength() != null && request.getContentLength() > 0) {
            uploadPartReqBuilder.contentLength(request.getContentLength());
        }

        // contentMD5 결속 (무결성). 클라이언트가 chunk 의 MD5 를 base64 로 보낸 경우만.
        if (request.getContentMd5Base64() != null && !request.getContentMd5Base64().isBlank()) {
            uploadPartReqBuilder.contentMD5(request.getContentMd5Base64());
        }

        UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(uploadProperties.getPresignedUrlTtlMinutes()))
                .uploadPartRequest(uploadPartReqBuilder.build())
                .build();

        PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(presignRequest);
        return presigned.url();
    }

    // ============================================================
    // 2-b) 모든 Part Presigned URL 일괄 발급
    // ============================================================

    /**
     * {@code fileSize} 만으로 part 개수/크기를 계산해 모든 part 의 presigned URL 을 한 번에 발급한다.
     *
     * <p>각 URL 은 해당 part 의 정확한 크기에 결속된다 — 마지막 part 만 잔여 바이트만큼 작을 수 있다.
     * 클라이언트는 받은 URL 들을 병렬 PUT 으로 사용한다.
     *
     * <p>요청에 {@code duration} 이 명시되어 있으면 단일 호출에서 그 값을 우선 사용한다 — 단,
     * 단일 발급기 {@link #generatePresignedUrl} 는 properties 의 TTL 을 사용하므로 상위 레이어에서 별도로
     * 일괄 처리를 원하면 본 메서드를 사용해야 일관된 만료시간이 보장된다.
     *
     * <p>무결성 결속 ({@code Content-MD5}) 이 필요하면 본 메서드 대신 {@link #generatePresignedUrl} 를
     * part 마다 호출하도록 한다 — 본 일괄 발급은 fileSize 만 알 뿐 part 별 본문 해시를 모르기 때문.
     *
     * @param request {@code key}/{@code uploadId}/{@code fileSize}/(옵션) {@code duration}
     * @return part 번호 1..n 순서의 presigned URL 응답 리스트
     */
    public List<S3ServiceDto.PartPresignedUrlResponse> generatePartPresignedUrls(
            S3ServiceDto.PartPresignedUrlRequest request) {
        int partCount = calculatePartCount(request.getFileSize());
        List<S3ServiceDto.PartPresignedUrlResponse> result = new ArrayList<>(partCount);

        for (int part = 1; part <= partCount; part++) {
            long partSize = calculatePartSize(request.getFileSize(), part, partCount);

            URL url = generatePresignedUrl(S3ServiceDto.PresignedUrlRequest.builder()
                    .key(request.getKey())
                    .uploadId(request.getUploadId())
                    .partNumber(part)
                    .contentLength(partSize)
                    .build());

            result.add(S3ServiceDto.PartPresignedUrlResponse.builder()
                    .partNumber(part)
                    .presignedUrl(url.toString())
                    .contentLength(partSize)
                    .build());
        }
        log.info("[S3] presigned URLs generated key={}, count={}", request.getKey(), partCount);
        return result;
    }

    // ============================================================
    // 3) Multipart Upload 완료
    // ============================================================

    /**
     * 모든 part 가 PUT 된 후 S3 에 part 병합 (commit) 을 요청한다.
     *
     * <p>S3 는 입력 part 목록이 {@code partNumber} 오름차순 정렬되어 있어야 함을 강제하므로
     * 본 메서드가 명시적으로 정렬 후 SDK 를 호출한다 — 클라이언트가 임의 순서로 보내도 안전.
     *
     * @param request {@code key}/{@code uploadId} + 클라이언트가 PUT 응답으로 받은 (partNumber, ETag) 목록
     * @return S3 의 {@code CompleteMultipartUploadResponse} (location, ETag, etc.)
     */
    public CompleteMultipartUploadResponse completeUpload(S3ServiceDto.CompleteUploadRequest request) {
        // partNumber 오름차순 정렬 필수 — 어기면 S3 가 InvalidPartOrder 로 거부.
        List<CompletedPart> completedParts = request.getParts().stream()
                .sorted(Comparator.comparing(S3ServiceDto.CompleteUploadRequest.Part::getPartNumber))
                .map(p -> CompletedPart.builder()
                        .partNumber(p.getPartNumber())
                        .eTag(p.getEtag())
                        .build())
                .toList();

        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(request.getKey())
                .uploadId(request.getUploadId())
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build();

        CompleteMultipartUploadResponse response = s3Client.completeMultipartUpload(completeRequest);
        log.info("[S3] multipart completed location={}, eTag={}", response.location(), response.eTag());
        return response;
    }

    // ============================================================
    // 4) Multipart Upload 중단 (orphan 정리)
    // ============================================================

    /**
     * 업로드를 중단하고 S3 에 누적된 part 들을 정리한다.
     *
     * <p>호출 트리거: 클라이언트의 명시적 abort, 사후 크기 검증 실패, 정책 위반 발견 등.
     * file_security.md §7.2 (orphan multipart cleanup) 의 적극적 정리에 해당한다.
     *
     * @param request {@code key}/{@code uploadId}
     */
    public void abortUpload(S3ServiceDto.AbortUploadRequest request) {
        s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(request.getKey())
                .uploadId(request.getUploadId())
                .build());
        log.info("[S3] multipart aborted key={}, uploadId={}", request.getKey(), request.getUploadId());
    }

    // ============================================================
    // 5) 재개(resume) 지원 — 이미 업로드된 part 조회
    // ============================================================

    /**
     * 현재까지 S3 에 도착한 part 목록을 조회한다 — 클라이언트가 네트워크 단절 후 재개할 때 어디까지
     * 올렸는지 확인하는 용도.
     *
     * <p>반환되는 {@code parts} 의 정렬 순서는 보장하지 않는다 — 호출자가 필요하면 정렬할 것.
     *
     * @param key      S3 객체 키
     * @param uploadId multipart upload 식별자
     * @return 업로드 완료된 part 들의 partNumber/ETag/size
     */
    public S3ServiceDto.ListPartsResponse listUploadedParts(String key, String uploadId) {
        software.amazon.awssdk.services.s3.model.ListPartsResponse s3Response =
                s3Client.listParts(ListPartsRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .uploadId(uploadId)
                        .build());

        List<S3ServiceDto.ListPartsResponse.UploadedPart> parts = s3Response.parts().stream()
                .map(p -> S3ServiceDto.ListPartsResponse.UploadedPart.builder()
                        .partNumber(p.partNumber())
                        .etag(p.eTag())
                        .size(p.size())
                        .build())
                .toList();

        return S3ServiceDto.ListPartsResponse.builder().parts(parts).build();
    }

    // ============================================================
    // 6) 객체 일괄 삭제 (DeleteObjects) — 1000개 청크 분할 + 부분 실패 허용
    // ============================================================

    /** AWS S3 {@code DeleteObjects} API 의 단일 요청 최대 키 개수 (AWS 한도). */
    private static final int DELETE_OBJECTS_BATCH_SIZE = 1000;

    /**
     * 주어진 S3 객체 키 목록을 batch {@code DeleteObjects} 호출로 일괄 삭제한다.
     *
     * <p>호출자({@code ArticleService.delete} 또는 {@code ArticleService.deleteAllByWriter}) 가 CDN URL 들에서
     * S3 키들을 추출해 본 메서드에 전달한다 — 본 메서드는 키 변환을 수행하지 않으며 순수히 batch 삭제만 책임진다.
     *
     * <p>구동 규칙:
     * <ul>
     *   <li>{@code keys} 가 {@code null} 이거나 비어있으면 즉시 반환 (no-op).</li>
     *   <li>{@code null}/blank 키는 사전 필터링 — DeleteObjects 요청에 포함되면 S3 가 {@code MalformedXML} 로 거부할 수 있다.</li>
     *   <li>중복 키는 제거 — 동일 키를 두 번 보내도 S3 가 한 번 삭제하지만 응답이 더 길어지고 quota 를 더 사용한다.</li>
     *   <li>AWS S3 {@code DeleteObjects} 한도(1000 키/요청) 에 맞춰 chunk={@value #DELETE_OBJECTS_BATCH_SIZE} 단위로 분할 호출.</li>
     *   <li>이미 존재하지 않는 키는 idempotent — unversioned bucket 에서 S3 는 missing key 도 성공(delete-marker semantics) 으로 응답.</li>
     *   <li>부분 실패는 로그 후 계속 진행 — 본 메서드는 예외를 전파하지 않는다 (fail-soft):
     *       Article 메타가 이미 DB 에서 삭제되거나 곧 삭제될 시점에 호출되며, 잔여 S3 객체는
     *       {@code file_security.md §7.2} 의 lifecycle 정책으로 회수되도록 한다.</li>
     *   <li>{@link S3Exception} 으로 chunk 전체가 실패하면 ERROR 로그만 남기고 다음 chunk 로 진행 — 한 chunk 의
     *       장애가 전체 정리를 막지 않게 한다.</li>
     * </ul>
     *
     * @param keys 삭제 대상 S3 객체 키 목록 (CDN URL prefix 가 이미 제거된 키). null/empty 허용 (no-op).
     */
    public void deleteObjects(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        // null/blank 제거 + 중복 제거 (입력 순서 보존: LinkedHashSet)
        List<String> sanitized = keys.stream()
                .filter(k -> k != null && !k.isBlank())
                .distinct()
                .toList();
        if (sanitized.isEmpty()) {
            return;
        }

        int totalKeys = sanitized.size();
        int totalChunks = (totalKeys + DELETE_OBJECTS_BATCH_SIZE - 1) / DELETE_OBJECTS_BATCH_SIZE;

        for (int chunkIdx = 0; chunkIdx < totalChunks; chunkIdx++) {
            int from = chunkIdx * DELETE_OBJECTS_BATCH_SIZE;
            int to = Math.min(from + DELETE_OBJECTS_BATCH_SIZE, totalKeys);
            List<String> chunk = sanitized.subList(from, to);

            List<ObjectIdentifier> identifiers = chunk.stream()
                    .map(k -> ObjectIdentifier.builder().key(k).build())
                    .toList();

            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(identifiers).quiet(Boolean.TRUE).build())
                    .build();

            try {
                DeleteObjectsResponse response = s3Client.deleteObjects(deleteRequest);

                // quiet 모드: 성공한 키는 응답에서 생략, 실패한 키만 errors() 로 반환.
                List<S3Error> errors = response.errors();
                if (errors != null && !errors.isEmpty()) {
                    for (S3Error err : errors) {
                        log.warn("[S3] deleteObjects partial failure key={}, code={}, message={}",
                                err.key(), err.code(), err.message());
                    }
                    log.warn("[S3] deleteObjects chunk={} ({}/{}) had {} failure(s) out of {} key(s)",
                            chunkIdx + 1, chunkIdx + 1, totalChunks, errors.size(), chunk.size());
                } else {
                    log.info("[S3] deleteObjects chunk={} ({}/{}) deleted {} key(s)",
                            chunkIdx + 1, chunkIdx + 1, totalChunks, chunk.size());
                }
            } catch (S3Exception e) {
                // 한 chunk 의 실패가 다음 chunk 정리를 막지 않도록 fail-soft.
                log.error("[S3] deleteObjects chunk={} ({}/{}) failed for {} key(s): {}",
                        chunkIdx + 1, chunkIdx + 1, totalChunks, chunk.size(), e.awsErrorDetails().errorMessage(), e);
            }
        }
    }

    // ============================================================
    // 사후 검증 — 실제 객체 크기 조회 (HeadObject)
    // ============================================================

    /**
     * complete 직후 호출해 S3 에 저장된 객체의 실제 바이트 크기를 반환한다.
     *
     * <p>호출자({@code FileService.completeMultipartUpload}) 가 클라이언트가 약속한 {@code fileSize} 와
     * 비교하여 불일치 시 {@link #abortUpload}{@code +} {@code RestException(FILE_SIZE_MISMATCH)} 처리한다 —
     * 약속보다 큰 본문을 청크 단위로 나누어 우회 업로드하는 시나리오 방어.
     *
     * @param key S3 객체 키
     * @return S3 가 보고하는 실제 크기 (bytes)
     */
    public long getObjectActualSize(String key) {
        HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
        return head.contentLength();
    }

    // ============================================================
    // Part 개수 / 크기 계산
    // ============================================================

    /**
     * 파일 크기로부터 multipart upload 의 part 개수를 계산한다.
     *
     * <p>전략:
     * <ul>
     *   <li>fileSize ≤ 0 또는 권장 part 크기 미만 → 1 (multipart 불필요한 작은 파일)</li>
     *   <li>그 외 → ceil(fileSize / RECOMMENDED_PART_SIZE)</li>
     *   <li>10000 part 초과 시 → ceil(fileSize / MAX_PART_SIZE) 로 폴백</li>
     *   <li>마지막 단계: part 평균 크기가 5MB 미만이면 (1 part 케이스 제외) 5MB 를 기준으로 재산정</li>
     * </ul>
     *
     * <p>경계값 검증:
     * <ul>
     *   <li>50MB → 1 (50MB &lt; 권장 64MB 임계점)</li>
     *   <li>100MB → 2 (ceil(100/64) = 2) — 백로그 한도와 동일</li>
     * </ul>
     *
     * @param fileSize 전체 파일 크기 (bytes), null/0 입력은 1 반환
     * @return part 개수 (1..{@link #MAX_PART_COUNT})
     */
    public Integer calculatePartCount(Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            return 1;
        }
        long recommendedPartSize = getRecommendedPartSize(fileSize);
        if (fileSize < recommendedPartSize) {
            return 1;
        }

        long partCount = (fileSize + recommendedPartSize - 1) / recommendedPartSize;

        if (partCount > MAX_PART_COUNT) {
            partCount = (fileSize + MAX_PART_SIZE - 1) / MAX_PART_SIZE;
        }

        long partSize = fileSize / partCount;
        if (partSize < MIN_PART_SIZE && partCount > 1) {
            partCount = fileSize / MIN_PART_SIZE;
            if (partCount == 0) partCount = 1;
        }

        return (int) Math.min(partCount, MAX_PART_COUNT);
    }

    /**
     * 특정 part 의 정확한 크기를 계산한다 (마지막 part 는 잔여 바이트).
     *
     * @param fileSize   전체 파일 크기 (bytes)
     * @param partNumber 1..totalParts
     * @param totalParts {@link #calculatePartCount(Long)} 결과
     * @return 해당 part 의 크기 (bytes)
     */
    public long calculatePartSize(Long fileSize, int partNumber, int totalParts) {
        if (totalParts == 1) return fileSize;
        long basePartSize = fileSize / totalParts;
        if (partNumber == totalParts) {
            return fileSize - (basePartSize * (totalParts - 1));
        }
        return basePartSize;
    }

    // ============================================================
    // Helpers (private)
    // ============================================================

    /**
     * S3 객체 키 생성. 형식: {@code files/{videos|images|pdf|other}/{UUID}__{sanitized_filename}}.
     *
     * <p>sanitization: 영문자/숫자/{@code .}/{@code _}/{@code -} 외 모든 문자를 제거한다 — {@code ../} 등의
     * 경로 traversal 시도는 모두 무력화된다 (file_security.md §3.2). 빈 파일명은 {@code unknown} 으로 대체.
     */
    private String generateKey(String subFolder, String originalFileName) {
        String safeName = (originalFileName == null || originalFileName.isBlank())
                ? "unknown"
                : originalFileName;
        safeName = safeName.replaceAll("[^a-zA-Z0-9._-]", "");
        if (safeName.isEmpty()) {
            safeName = "unknown";
        }
        return subFolder + "/" + UUID.randomUUID() + "__" + safeName;
    }

    /**
     * Content-Type 추정. 클라이언트가 보낸 {@code mimeType} 을 1순위로, 없으면 파일명에서 추정,
     * 그래도 없으면 {@code application/octet-stream} fallback.
     */
    private String guessContentType(String mimeType, String fileName) {
        String contentType = mimeType;
        if (contentType == null || contentType.isBlank()) {
            contentType = URLConnection.guessContentTypeFromName(fileName);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
        }
        return contentType;
    }

    /**
     * 파일 크기에 따른 권장 part 크기. 1GB 미만 → 64MB, 1GB 이상 → 128MB.
     */
    private static long getRecommendedPartSize(long fileSize) {
        return fileSize < LARGE_FILE_THRESHOLD
                ? RECOMMENDED_PART_SIZE_SMALL
                : RECOMMENDED_PART_SIZE_LARGE;
    }

    /**
     * S3 객체 키의 subfolder 결정 (항상 {@code files/} 아래 2단계 경로).
     *
     * <p>{@link MediaType}: {@code VIDEO}/{@code AUDIO} → {@code files/videos/}, {@code IMAGE} → {@code files/images/},
     * {@code PDF} → {@code files/pdf/}. {@code UNKNOWN} 또는 {@code null} 분류는 MIME·확장자로 재판정하고,
     * 그래도 불명확하면 {@code files/other/}.
     */
    private String determineSubFolder(MediaType mediaType, String mimeType, String fileName) {
        if (mediaType != null) {
            return switch (mediaType) {
                case VIDEO, AUDIO -> "files/videos";
                case IMAGE -> "files/images";
                case PDF -> "files/pdf";
                case UNKNOWN -> resolveSubFolderFromMimeAndName(mimeType, fileName);
            };
        }
        return resolveSubFolderFromMimeAndName(mimeType, fileName);
    }

    /**
     * MIME prefix·파일명 확장자로 {@code files/videos} … 중 선택; 매칭 실패 시 {@code files/other}
     * (단일 루트 {@code files/uuid…} 키는 쓰지 않음).
     */
    private static String resolveSubFolderFromMimeAndName(String mimeType, String fileName) {
        String type = mimeType != null ? mimeType.toLowerCase() : "";
        String name = fileName != null ? fileName.toLowerCase() : "";
        if (type.startsWith("video/") || type.startsWith("audio/")
                || name.matches(".*\\.(mp4|avi|mov|wmv|flv|webm|mkv)$")) {
            return "files/videos";
        }
        if (type.startsWith("image/")
                || name.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|svg)$")) {
            return "files/images";
        }
        if ("application/pdf".equals(type) || name.endsWith(".pdf")) {
            return "files/pdf";
        }
        return "files/other";
    }
}
