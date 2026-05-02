package com.capstone.passfolio.domain.file.controller;

import com.capstone.passfolio.domain.file.dto.FileDto;
import com.capstone.passfolio.domain.file.entity.File;
import com.capstone.passfolio.domain.file.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * S3 Multipart Upload REST 엔드포인트 5종 구현체.
 *
 * <p>본 컨트롤러는 {@link FileApiSpecification} (T14, OpenAPI 메타데이터 전용 인터페이스) 의 5개 메서드를
 * 구현하며, Spring 라우팅(@{@code RestController}, @{@code RequestMapping}, @{@code PostMapping},
 * @{@code GetMapping}) 과 입력 바인딩(@{@code RequestBody}, @{@code RequestParam}, @{@code Valid})
 * 만 본 클래스에 추가한다 — 프로젝트 표준 패턴 ({@code AuthController}, {@code DevSpecController})
 * 과 동일.
 *
 * <p>실제 비즈니스 로직은 모두 {@link FileService} 가 담당하며, 본 컨트롤러는 얇은 위임 계층이다 — 입력
 * DTO 를 그대로 서비스에 넘기고, 서비스 결과를 응답 DTO 로 wrap (또는 {@link FileDto.UploadFileResponse#of}
 * 변환) 하여 반환할 뿐이다. 예외는 모두 {@code RestException} 으로 던져져 글로벌 핸들러가 상태코드 + 본문
 * 으로 직렬화한다 (validator 가 검증할 표면).
 *
 * <p>HTTP 상태 매핑 (설계 §"Interface contracts" + T14 spec 과 일치):
 * <ul>
 *   <li>{@code POST /multipart/initiate} → 200 / 400 / 413 / 502</li>
 *   <li>{@code POST /multipart/part-presigned-url} → 200 / 400 / 502</li>
 *   <li>{@code GET /multipart/list-parts} → 200 / 502</li>
 *   <li>{@code POST /multipart/complete} → 200 / 400 / 502</li>
 *   <li>{@code POST /multipart/abort} → 204 / 400 / 502</li>
 * </ul>
 *
 * <p>설계 참조: {@code 0001-harness-design.md} §"Files plan / FileController" + §"Interface contracts".
 *
 * @see FileApiSpecification
 * @see FileService
 * @see FileDto
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/files")
public class FileController implements FileApiSpecification {

    private final FileService fileService;

    // ============================================================
    // 1) POST /api/v1/files/multipart/initiate
    // ============================================================

    /**
     * 멀티파트 업로드 초기화 — {@link FileService#initiateMultipartUpload} 위임.
     *
     * <p>{@code @Valid} 가 {@link FileDto.MultipartUploadInitiateRequest} 의 {@code @NotBlank originalFileName},
     * {@code @NotNull @Min(1) fileSize} 를 1차 검증 (위반 시 400 + Bean Validation 본문). 통과한 입력만
     * 서비스로 전달되어 정책 검증 (확장자 + 100MB 한도) 을 거친 후 presigned URL 을 일괄 발급한다.
     */
    @Override
    @PostMapping("/multipart/initiate")
    public ResponseEntity<FileDto.MultipartUploadInitiateResponse> initiateMultipartUpload(
            @Valid @RequestBody FileDto.MultipartUploadInitiateRequest request) {
        return ResponseEntity.ok(fileService.initiateMultipartUpload(request));
    }

    // ============================================================
    // 2) POST /api/v1/files/multipart/part-presigned-url
    // ============================================================

    /**
     * 단일 파트 Presigned URL 발급 — {@link FileService#generatePartPresignedUrl} 위임.
     *
     * <p>무결성 결속 (Content-MD5) 가 필요한 클라이언트 플로우. {@code @Valid} 가 {@code key/uploadId/partNumber/contentLength}
     * 의 필수성 + 범위 (1..10000 / ≥ 1) 를 1차 검증한다. {@code md5Base64} 는 옵션이며 비어있으면
     * S3 서명에 결속되지 않는다 (서비스가 처리).
     */
    @Override
    @PostMapping("/multipart/part-presigned-url")
    public ResponseEntity<FileDto.PartPresignedUrlSingleResponse> generatePartPresignedUrl(
            @Valid @RequestBody FileDto.PartPresignedUrlSingleRequest request) {
        return ResponseEntity.ok(fileService.generatePartPresignedUrl(request));
    }

    // ============================================================
    // 3) GET /api/v1/files/multipart/list-parts
    // ============================================================

    /**
     * 이미 업로드된 part 목록 조회 — {@link FileService#listUploadedParts} 위임.
     *
     * <p>쿼리 파라미터 {@code key}, {@code uploadId} 는 모두 필수. 누락 시 Spring 이 400 으로 거부한다
     * (별도 {@code @Valid} 불필요 — {@code @RequestParam} 의 {@code required=true} 기본값이 강제).
     */
    @Override
    @GetMapping("/multipart/list-parts")
    public ResponseEntity<FileDto.ListPartsResponse> listUploadedParts(
            @RequestParam("key") String key,
            @RequestParam("uploadId") String uploadId) {
        return ResponseEntity.ok(fileService.listUploadedParts(key, uploadId));
    }

    // ============================================================
    // 4) POST /api/v1/files/multipart/complete
    // ============================================================

    /**
     * 멀티파트 업로드 완료 — {@link FileService#completeMultipartUpload} 위임 후
     * {@link FileDto.UploadFileResponse#of(File)} 로 응답 변환.
     *
     * <p>서비스가 영속화된 {@link File} 엔티티를 반환하면 {@code UploadFileResponse.of} 가 {@code cdn.base-url}
     * 을 결합해 {@code cdnUrl} 을 도출한다 ({@code cdn.base-url} 미설정 시 {@code IllegalStateException}
     * 이 전파되어 글로벌 핸들러가 500 으로 응답 — fail-fast).
     *
     * <p>사후 크기 검증 실패 시 서비스가 {@code FILE_SIZE_MISMATCH} 로 throw (글로벌 핸들러가 400) 하고
     * 동일 트랜잭션에서 S3 abort 가 실행되어 객체가 남지 않는다 (file_security.md §3.4).
     */
    @Override
    @PostMapping("/multipart/complete")
    public ResponseEntity<FileDto.UploadFileResponse> completeMultipartUpload(
            @Valid @RequestBody FileDto.CompleteMultipartUploadRequest request) {
        File saved = fileService.completeMultipartUpload(request);
        return ResponseEntity.ok(FileDto.UploadFileResponse.of(saved));
    }

    // ============================================================
    // 5) POST /api/v1/files/multipart/abort
    // ============================================================

    /**
     * 멀티파트 업로드 취소 — {@link FileService#abortMultipartUpload} 위임. 응답 본문 없이 204 No Content
     * 반환 (T14 spec 의 {@code responseCode = "204"} 와 일치).
     *
     * <p>S3 lifecycle rule 과의 보완 관계는 file_security.md §7.2 — orphan multipart cleanup 참조.
     */
    @Override
    @PostMapping("/multipart/abort")
    public ResponseEntity<Void> abortMultipartUpload(
            @Valid @RequestBody FileDto.MultipartUploadAbortRequest request) {
        fileService.abortMultipartUpload(request.getKey(), request.getUploadId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
