package com.capstone.passfolio.domain.file.controller;

import com.capstone.passfolio.domain.file.dto.FileDto;
import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * S3 Multipart Upload REST API 의 OpenAPI(Swagger) 명세 인터페이스.
 *
 * <p>구현은 {@link FileController} 가 담당하며 (T15), 본 인터페이스는 오직 Swagger UI / OpenAPI 문서용
 * 메타데이터 (요약, 설명, 응답 스키마, 에러 예시) 만을 보유한다. 프로젝트의 다른 ApiSpecification
 * (e.g. {@code AuthApiSpecification}, {@code UserApiSpecification}) 과 동일한 패턴이다.
 *
 * <p>두 가지 발급 플로우 (skill SKILL.md §4):
 * <ol>
 *   <li><b>간단 케이스</b>: {@code POST /multipart/initiate} 한 번으로 모든 part presigned URL 일괄 수령.
 *       MD5 결속 X. {@code Content-Length} 만 서명에 결속.</li>
 *   <li><b>무결성 케이스</b>: initiate 응답의 {@code partPresignedUrls} 를 무시하고, 각 part 마다 클라이언트가
 *       MD5(Base64) 를 계산해 {@code POST /multipart/part-presigned-url} 로 한 개씩 받는다.
 *       {@code Content-MD5} 까지 서명에 결속되어 변조 0% 보장.</li>
 * </ol>
 *
 * <p>모든 엔드포인트는 비인증(permitAll)으로 노출되며 (본 백로그 범위), 향후 별도 백로그에서 인증 게이트가
 * 추가될 수 있다. {@code S3Service} / {@code FileService} 가 던지는 {@code RestException} 은
 * 글로벌 핸들러에 의해 표준 {@link ErrorResponse} JSON 으로 직렬화된다.
 *
 * <p>설계 참조: {@code 0001-harness-design.md} §"Files plan / FileApiSpecification" + §"Interface contracts".
 *
 * @see FileController
 * @see FileDto
 * @see com.capstone.passfolio.system.exception.model.ErrorCode
 */
@Tag(
        name = "File",
        description =
                """
                S3 Multipart Upload API — Direct-to-S3 분할 업로드 presigned URL 발급/완료/취소.

                ## 정책 (백로그 강제값)
                - 허용 확장자(화이트리스트): `pdf, jpg, jpeg, png, gif, webp, bmp, svg, mp4, mov, webm, avi, wmv, flv, mkv`
                - 최대 파일 크기: **100 MB** (104857600 byte)
                - presigned URL TTL: 10분 (운영 가능 범위 ≤ 15분)

                ## 보안 결속 (file_security.md §3)
                - 모든 part URL 에 `Content-Length` 결속 — 약속한 크기 외의 PUT 거부 (DoS 방어)
                - `Content-MD5` 는 클라이언트가 `md5Base64` 를 보낼 때만 결속 (무결성 강화 옵션)
                - 업로드 완료 후 `HeadObject` 로 실제 크기를 재검증 — 불일치 시 즉시 abort

                ## S3 객체 키 prefix (서버 확정)
                - 모든 업로드 객체는 `files/` 아래에 저장한다.
                - 동영상·오디오 계열 → `files/videos/`
                - 이미지 → `files/images/`
                - PDF → `files/pdf/`
                - 분류 불명(UNKNOWN 등) → MIME·확장자로 재판정, 여전히 불명확하면 `files/other/`
                """)
public interface FileApiSpecification {

    // ============================================================
    // 1) POST /api/v1/files/multipart/initiate
    // ============================================================

    @Operation(
            summary = "멀티파트 업로드 초기화",
            description =
                    """
                    파일 메타로 `uploadId` + 모든 part 의 presigned URL 을 일괄 발급한다.

                    ## 처리 흐름
                    1. `validateUploadPolicy` — 확장자 화이트리스트 + 100MB 한도 사전 거부 (cheap reject)
                    2. `S3Service.initiateUpload` — UUID 기반 서버 권위의 `key` 확정 + S3 가 발급한 `uploadId` 수령
                    3. `calculatePartCount(fileSize)` 만큼 일괄 presigned URL 발급 (`Content-Length` 결속)

                    ## 사용 가이드
                    - **간단 케이스**: 응답의 `partPresignedUrls` 를 그대로 클라이언트가 PUT 에 사용
                    - **무결성 케이스**: 응답의 `partPresignedUrls` 를 무시하고 `/multipart/part-presigned-url` 을 part 마다 호출

                    ## 주의사항
                    - 클라이언트가 보낸 `originalFileName` 은 표시용으로만 보존되며, S3 객체 키에는 직접 사용되지 않는다 (file_security.md §3.2 — path traversal 방어).
                    - `mimeType` 이 누락되면 서버가 확장자에서 추론한다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "초기화 성공 — `uploadId` + 일괄 발급된 part presigned URL",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                FileDto.MultipartUploadInitiateResponse
                                                                        .class),
                                        examples =
                                                @ExampleObject(
                                                        name = "PDF 1MB 업로드",
                                                        value =
                                                                """
                                                                {
                                                                  "key": "files/pdf/3f2d04ec-12ab-4cde-9abc-1234567890ab__resume.pdf",
                                                                  "uploadId": "upload-id-12345",
                                                                  "contentType": "application/pdf",
                                                                  "partCount": 1,
                                                                  "partPresignedUrls": [
                                                                    {
                                                                      "partNumber": 1,
                                                                      "presignedUrl": "https://bucket.s3.ap-northeast-2.amazonaws.com/files/pdf/3f2d04ec-...?X-Amz-Algorithm=...",
                                                                      "contentLength": 1048576
                                                                    }
                                                                  ]
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                """
                                요청 검증 실패

                                | ErrorCode | 상황 | 해결 방법 |
                                |-----------|------|----------|
                                | `FILE_EXTENSION_NOT_ALLOWED` | 화이트리스트에 없는 확장자 (e.g. `.exe`) | 허용 확장자로 변경 |
                                | `FILE_INVALID_SIZE` | `fileSize` 가 1 미만 | 양수 byte 값 전달 |
                                | (Bean Validation) | `originalFileName` 누락 / `fileSize` 누락 | 요청 본문 보정 |
                                """,
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples = {
                                            @ExampleObject(
                                                    name = "FILE_EXTENSION_NOT_ALLOWED",
                                                    value =
                                                            """
                                                            {
                                                              "status": "BAD_REQUEST",
                                                              "error": "FILE EXTENSION NOT ALLOWED",
                                                              "message": "허용되지 않는 파일 확장자입니다."
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "FILE_INVALID_SIZE",
                                                    value =
                                                            """
                                                            {
                                                              "status": "BAD_REQUEST",
                                                              "error": "FILE INVALID SIZE",
                                                              "message": "파일 크기가 유효하지 않습니다."
                                                            }
                                                            """)
                                        })),
                @ApiResponse(
                        responseCode = "413",
                        description = "`fileSize` 가 100MB 한도 초과",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "FILE_SIZE_EXCEEDED",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "PAYLOAD_TOO_LARGE",
                                                                  "error": "FILE SIZE EXCEEDED",
                                                                  "message": "파일 크기가 허용된 한도를 초과했습니다."
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "502",
                        description = "S3 SDK 호출 실패",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "FILE_UPLOAD_S3_ERROR",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "BAD_GATEWAY",
                                                                  "error": "FILE UPLOAD S3 ERROR",
                                                                  "message": "S3 업로드 처리 중 오류가 발생했습니다."
                                                                }
                                                                """)))
            })
    ResponseEntity<FileDto.MultipartUploadInitiateResponse> initiateMultipartUpload(
            FileDto.MultipartUploadInitiateRequest request);

    // ============================================================
    // 2) POST /api/v1/files/multipart/part-presigned-url
    // ============================================================

    @Operation(
            summary = "단일 파트 Presigned URL 발급 (무결성 결속 옵션)",
            description =
                    """
                    클라이언트가 chunk 을 슬라이스 → MD5(Base64) 계산 → 본 API 호출의 무결성 강화 플로우용.

                    ## 처리 흐름
                    1. `S3Service.generatePresignedUrl` — `Content-Length` 는 항상 결속, `md5Base64` 가 있으면 `Content-MD5` 도 결속

                    ## 사용 가이드
                    - 응답으로 받은 `presignedUrl` 에 PUT 시 클라이언트는 반드시:
                      - `Content-Length` 헤더 = 응답 `contentLength` 값
                      - `md5Base64` 를 보냈다면 `Content-MD5` 헤더 = 응답 `md5Base64` 값
                      을 그대로 포함해야 한다. 어느 하나라도 다르면 S3 가 서명 불일치로 거부한다.

                    ## 주의사항
                    - `partNumber` 는 1..10000 범위. 동일 part 를 여러 번 PUT 하면 마지막 PUT 만 유효.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Presigned URL 발급 성공",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                FileDto.PartPresignedUrlSingleResponse
                                                                        .class),
                                        examples =
                                                @ExampleObject(
                                                        name = "MD5 결속 64MB 파트",
                                                        value =
                                                                """
                                                                {
                                                                  "partNumber": 1,
                                                                  "presignedUrl": "https://bucket.s3.ap-northeast-2.amazonaws.com/files/pdf/3f2d04ec-...?partNumber=1&uploadId=upload-id-12345&X-Amz-Algorithm=...",
                                                                  "contentLength": 67108864,
                                                                  "md5Base64": "8gWuEUu3Q5Qbd7Q+aXhP+w=="
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "400",
                        description = "요청 검증 실패 — `key`/`uploadId` 누락, `partNumber` 범위 위반, `contentLength` < 1 등",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "502",
                        description = "S3 SDK 호출 실패",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "FILE_UPLOAD_S3_ERROR",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "BAD_GATEWAY",
                                                                  "error": "FILE UPLOAD S3 ERROR",
                                                                  "message": "S3 업로드 처리 중 오류가 발생했습니다."
                                                                }
                                                                """)))
            })
    ResponseEntity<FileDto.PartPresignedUrlSingleResponse> generatePartPresignedUrl(
            FileDto.PartPresignedUrlSingleRequest request);

    // ============================================================
    // 3) GET /api/v1/files/multipart/list-parts
    // ============================================================

    @Operation(
            summary = "이미 업로드된 part 목록 조회 (재개용)",
            description =
                    """
                    네트워크 단절 후 클라이언트가 어디까지 PUT 했는지 확인하기 위한 조회.

                    ## 처리 흐름
                    1. `S3Service.listUploadedParts` — S3 의 `ListParts` 호출

                    ## 응답 정렬
                    - `partNumber` 정렬 보장 없음. 클라이언트가 필요시 정렬해서 사용.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "현재까지 S3 가 받은 파트 목록",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema =
                                                @Schema(implementation = FileDto.ListPartsResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "2개 파트 업로드된 상태",
                                                        value =
                                                                """
                                                                {
                                                                  "parts": [
                                                                    {
                                                                      "partNumber": 1,
                                                                      "etag": "\\"etag-value-1\\"",
                                                                      "size": 67108864
                                                                    },
                                                                    {
                                                                      "partNumber": 2,
                                                                      "etag": "\\"etag-value-2\\"",
                                                                      "size": 37748736
                                                                    }
                                                                  ]
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "502",
                        description = "S3 SDK 호출 실패",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "FILE_UPLOAD_S3_ERROR",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "BAD_GATEWAY",
                                                                  "error": "FILE UPLOAD S3 ERROR",
                                                                  "message": "S3 업로드 처리 중 오류가 발생했습니다."
                                                                }
                                                                """)))
            })
    ResponseEntity<FileDto.ListPartsResponse> listUploadedParts(
            @Parameter(
                            description = "initiate 응답의 `key`",
                            required = true,
                            example = "files/pdf/3f2d04ec-12ab-4cde-9abc-1234567890ab__resume.pdf")
                    String key,
            @Parameter(
                            description = "initiate 응답의 `uploadId`",
                            required = true,
                            example = "upload-id-12345")
                    String uploadId);

    // ============================================================
    // 4) POST /api/v1/files/multipart/complete
    // ============================================================

    @Operation(
            summary = "멀티파트 업로드 완료",
            description =
                    """
                    클라이언트가 모든 파트 PUT 을 마친 뒤 호출. S3 가 파트들을 병합하고, 서버는 사후 크기 검증을 거쳐 `File` 엔티티를 영속화한다.

                    ## 처리 흐름
                    1. `S3Service.completeUpload` — `parts` 를 `partNumber` 오름차순 정렬 후 S3 의 `CompleteMultipartUpload` 호출
                    2. `S3Service.getObjectActualSize` — `HeadObject` 로 실제 객체 크기 조회
                    3. 약속한 `fileSize` ≠ 실제 크기 → `S3Service.abortUpload` + `FILE_SIZE_MISMATCH` 발생
                    4. 검증 통과 → `FileRepository.save` → `UploadFileResponse.of(File)` 반환 (`cdnUrl` 포함)

                    ## 보안 (file_security.md §3.4)
                    - 사후 크기 검증은 클라이언트가 거짓 `fileSize` 를 보낸 뒤 더 큰 객체를 PUT 하는 공격 시나리오를 막기 위함이다.
                    - 검증 실패 시 즉시 abort 되므로 S3 에 객체가 남지 않는다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "업로드 완료 + DB 영속화 성공",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = FileDto.UploadFileResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "PDF 업로드 완료",
                                                        value =
                                                                """
                                                                {
                                                                  "fileId": 12345,
                                                                  "filename": "resume.pdf",
                                                                  "cdnUrl": "https://cdn.passfolio.com/files/pdf/3f2d04ec-12ab-4cde-9abc-1234567890ab__resume.pdf",
                                                                  "fileSize": 1048576,
                                                                  "mediaType": "PDF",
                                                                  "status": "AVAILABLE"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                """
                                요청 검증 실패 또는 사후 크기 불일치

                                | ErrorCode | 상황 |
                                |-----------|------|
                                | `FILE_SIZE_MISMATCH` | 약속한 `fileSize` ≠ 실제 객체 크기 (`HeadObject` 결과). 자동 abort 됨 |
                                | (Bean Validation) | `key`/`uploadId`/`originalFileName` 누락, `parts` 비어있음, `fileSize` < 1 |
                                """,
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "FILE_SIZE_MISMATCH",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "BAD_REQUEST",
                                                                  "error": "FILE SIZE MISMATCH",
                                                                  "message": "업로드된 파일 크기가 약속한 크기와 일치하지 않습니다."
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "502",
                        description = "S3 SDK 호출 실패 (`CompleteMultipartUpload` / `HeadObject`)",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "FILE_UPLOAD_S3_ERROR",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "BAD_GATEWAY",
                                                                  "error": "FILE UPLOAD S3 ERROR",
                                                                  "message": "S3 업로드 처리 중 오류가 발생했습니다."
                                                                }
                                                                """)))
            })
    ResponseEntity<FileDto.UploadFileResponse> completeMultipartUpload(
            FileDto.CompleteMultipartUploadRequest request);

    // ============================================================
    // 5) POST /api/v1/files/multipart/abort
    // ============================================================

    @Operation(
            summary = "멀티파트 업로드 취소",
            description =
                    """
                    클라이언트가 명시적으로 취소하거나, 진행 도중 영구 실패가 감지되었을 때 호출.

                    ## 처리 흐름
                    1. `S3Service.abortUpload` — S3 의 `AbortMultipartUpload` 호출 (중간 파트들을 정리)

                    ## 운영 가이드 (file_security.md §7.2)
                    - 클라이언트가 abort 호출을 못하고 죽는 경우(브라우저 종료 등)를 대비해, S3 lifecycle rule
                      (예: 7일 이상 미완료 multipart 자동 삭제) 도 함께 운영하는 것이 권장된다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "204",
                        description = "취소 성공 (응답 본문 없음)"),
                @ApiResponse(
                        responseCode = "400",
                        description = "요청 검증 실패 — `key` 또는 `uploadId` 누락",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "502",
                        description = "S3 SDK 호출 실패",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "FILE_UPLOAD_S3_ERROR",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "BAD_GATEWAY",
                                                                  "error": "FILE UPLOAD S3 ERROR",
                                                                  "message": "S3 업로드 처리 중 오류가 발생했습니다."
                                                                }
                                                                """)))
            })
    ResponseEntity<Void> abortMultipartUpload(FileDto.MultipartUploadAbortRequest request);
}
