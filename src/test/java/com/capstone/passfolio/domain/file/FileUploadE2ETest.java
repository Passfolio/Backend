package com.capstone.passfolio.domain.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.passfolio.domain.file.repository.FileRepository;
import com.capstone.passfolio.support.AbstractIntegrationTest;

import java.net.URI;
import java.net.URL;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;

/**
 * S3 멀티파트 업로드 직접 흐름의 단대단(E2E) 테스트.
 *
 * <p>설계 참조: {@code 0001-harness-design.md} Tasks T21 + §"Acceptance signals" 의 E2E 행
 * (positive happy-path + 4 negative scenarios). 백로그 제약 "외부 API 를 사용하므로 Mock 기반"
 * 을 준수하기 위해 {@link S3Client}/{@link S3Presigner} 를 {@link MockitoBean} 으로 치환한다 — 실제
 * AWS 호출 0회.
 *
 * <p><b>왜 E2E 인가</b> — controller slice (T19) 는 {@link FileService} 를 mock 으로 두기 때문에
 * 실제 흐름에서 {@code initiate → S3 SDK 호출 → Presigner 호출 → DTO 매핑 → JSON 직렬화 → ErrorResponse
 * (RestException)} 의 모든 단계가 잘 엮여 있는지를 검증할 수 없다. 본 테스트는 Spring 컨텍스트 전체를
 * 띄워 글로벌 ExceptionHandler / Validator / Jackson / JPA 가 실제 컴포지션과 동일하게 동작하는지를
 * 보장한다 (springboot-test 스킬 §4 — Integration tier 용도).
 *
 * <p><b>검증 명세 (acceptance signals 와 1:1 매핑)</b>:
 * <ul>
 *   <li>Positive: {@code initiate(.pdf, 100MB) → 200} + {@code key/uploadId/contentType/partCount/partPresignedUrls}
 *       응답 JSON 명확 + S3 SDK {@code createMultipartUpload} 1회 호출 + part 수 만큼 Presigner 호출.</li>
 *   <li>Positive: {@code complete(actualSize == fileSize) → 200} + {@code UploadFileResponse} 의 모든 필드
 *       (fileId/filename/cdnUrl/fileSize/mediaType/status="AVAILABLE") 명확 + DB 영속화 확인.</li>
 *   <li>Negative-1: {@code initiate(.exe) → 400} + {@code error="FILE_EXTENSION_NOT_ALLOWED"} +
 *       SDK 호출 0회 (cheap reject 명세).</li>
 *   <li>Negative-2: {@code initiate(200MB) → 413} + {@code error="FILE_SIZE_EXCEEDED"} + SDK 호출 0회.</li>
 *   <li>Negative-3: {@code complete(headObject 가 다른 size 반환) → 400} + {@code error="FILE_SIZE_MISMATCH"}
 *       + SDK {@code abortMultipartUpload} 정확 1회 호출 (영속화 사후 검증 명세 — file_security.md §3.4).</li>
 *   <li>Negative-4: {@code abort(정상 입력) → 204 + 본문 비어있음} + SDK {@code abortMultipartUpload} 정확
 *       1회 호출 (orphan 정리 명세 — file_security.md §7.2).</li>
 * </ul>
 *
 * <p><b>제외</b>: 인증/인가 (design 결정 §"No JWT/auth gate" 로 본 백로그 범위 외; {@code addFilters=false}
 * 로 SecurityFilterChain 우회) — 향후 auth wiring 백로그가 별도 검증.
 *
 * <p><b>Skill 적용</b>: {@code springboot-test} (§4 — Integration). {@code @SpringBootTest} +
 * {@code @MockitoBean} (Spring Boot 3.4+ 권장; deprecated {@code @MockBean} 미사용) + {@code MockMvc}
 * 골격 채택. {@code spring-s3-multipart-upload} 스킬은 production 템플릿 전용이라 직접 적용 없음.
 */
@AutoConfigureMockMvc(addFilters = false)
class FileUploadE2ETest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileRepository fileRepository;

    // S3Client / S3Presigner / RedissonClient 는 AbstractIntegrationTest 가 protected mock 으로 노출.
    // CareerDataInitializer / UniversityDataInitializer 는 @Profile("!test") 로 컨텍스트에 미등록.

    /**
     * 결정성: 모든 테스트가 동일한 mock S3 응답을 받도록 setUp 에서 일괄 stubbing.
     *
     * <p>각 테스트의 specific behavior (예: {@code headObject} 가 size mismatch 를 일으키는 시나리오)
     * 는 해당 테스트가 자신의 stub 으로 override 한다. Mockito 의 lenient stubbing 으로 unused stub
     * 경고를 차단 — E2E 테스트는 의도적으로 "모든 SDK 호출이 안전하게 mock 되어있다" 를 baseline 으로
     * 가정하기 때문.
     */
    @BeforeEach
    void setUpDefaultS3Stubs() throws Exception {
        // initiate → 고정 uploadId 반환
        given(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .willReturn(CreateMultipartUploadResponse.builder()
                        .uploadId("test-upload-id-12345")
                        .build());

        // 모든 part presigned URL → 고정 https URL
        URL fakeUrl = URI.create("https://test-bucket.s3.amazonaws.com/presigned?X-Amz=fake").toURL();
        PresignedUploadPartRequest presigned =
                org.mockito.Mockito.mock(PresignedUploadPartRequest.class);
        given(presigned.url()).willReturn(fakeUrl);
        given(s3Presigner.presignUploadPart(any(software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest.class)))
                .willReturn(presigned);

        // complete → 고정 location/etag (clean shutdown)
        given(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .willReturn(CompleteMultipartUploadResponse.builder()
                        .location("https://test-bucket.s3.amazonaws.com/files/pdf/x")
                        .eTag("\"final-etag\"")
                        .build());

        // abort → noop response (RestException 회피)
        given(s3Client.abortMultipartUpload(any(AbortMultipartUploadRequest.class)))
                .willReturn(AbortMultipartUploadResponse.builder().build());

        // headObject 기본: 100MB (positive happy-path 와 일치). 음수 시나리오는 개별 stub override.
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().contentLength(104_857_600L).build());
    }

    @AfterEach
    void cleanupPersistedFiles() {
        // 각 테스트가 같은 s3ObjectKey 를 쓸 수도 있으므로 (UK_FILES_S3_OBJECT_KEY 충돌 방지) 정리.
        fileRepository.deleteAll();
    }

    // ============================================================
    // POSITIVE — happy-path
    // ============================================================

    @Nested
    @DisplayName("Positive 시나리오")
    class Positive {

        @Test
        @DisplayName("initiate(.pdf, 100MB) → 200 + 응답 JSON shape (key/uploadId/contentType/partCount/partPresignedUrls) + S3 SDK 정확히 1회 호출")
        void initiate_pdfAt100MB_returns200_andCallsCreateMultipartUploadOnce() throws Exception {
            String requestBody = """
                    {
                      "originalFileName": "resume.pdf",
                      "mimeType": "application/pdf",
                      "fileSize": 104857600
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.uploadId").value("test-upload-id-12345"))
                    .andExpect(jsonPath("$.contentType").value("application/pdf"))
                    // 100MB 는 권장 64MB 임계점 초과 → ceil(100/64)=2 part (S3Service.calculatePartCount 명세)
                    .andExpect(jsonPath("$.partCount").value(2))
                    .andExpect(jsonPath("$.partPresignedUrls.length()").value(2))
                    // 서버가 키를 files/(videos|images|pdf|other)/UUID__sanitized 형식으로 확정 (file_security.md §3.2)
                    .andExpect(jsonPath("$.key", org.hamcrest.Matchers.matchesPattern(
                            "^files/pdf/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}__resume\\.pdf$")))
                    .andExpect(jsonPath("$.partPresignedUrls[0].partNumber").value(1))
                    .andExpect(jsonPath("$.partPresignedUrls[0].presignedUrl")
                            .value("https://test-bucket.s3.amazonaws.com/presigned?X-Amz=fake"))
                    .andExpect(jsonPath("$.partPresignedUrls[1].partNumber").value(2));

            // SDK 호출 검증 — initiate 는 createMultipartUpload 정확히 1회.
            ArgumentCaptor<CreateMultipartUploadRequest> captor =
                    ArgumentCaptor.forClass(CreateMultipartUploadRequest.class);
            then(s3Client).should(times(1)).createMultipartUpload(captor.capture());
            CreateMultipartUploadRequest sdkRequest = captor.getValue();
            assertThat(sdkRequest.bucket()).isEqualTo("test-bucket");
            assertThat(sdkRequest.contentType()).isEqualTo("application/pdf");
            assertThat(sdkRequest.key())
                    .matches("^files/pdf/[0-9a-f-]{36}__resume\\.pdf$");

            // partCount=2 만큼 presignUploadPart 호출
            then(s3Presigner).should(times(2))
                    .presignUploadPart(any(software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest.class));
        }

        @Test
        @DisplayName("complete(actualSize==fileSize) → 200 + UploadFileResponse JSON (fileId/filename/cdnUrl/fileSize/mediaType=PDF/status=AVAILABLE) + DB 영속화")
        void complete_actualMatchesPromised_returns200_andPersistsFile() throws Exception {
            String key = "files/pdf/" + UUID.randomUUID() + "__resume.pdf";
            given(s3Client.headObject(any(HeadObjectRequest.class)))
                    .willReturn(HeadObjectResponse.builder().contentLength(104_857_600L).build());

            String requestBody = """
                    {
                      "key": "%s",
                      "uploadId": "test-upload-id-12345",
                      "parts": [
                        { "partNumber": 1, "etag": "\\"etag-1\\"" },
                        { "partNumber": 2, "etag": "\\"etag-2\\"" }
                      ],
                      "originalFileName": "resume.pdf",
                      "fileSize": 104857600,
                      "mimeType": "application/pdf"
                    }
                    """.formatted(key);

            mockMvc.perform(post("/api/v1/files/multipart/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileId").isNumber())
                    .andExpect(jsonPath("$.filename").value("resume.pdf"))
                    .andExpect(jsonPath("$.cdnUrl").value("https://cdn.passfolio.test/" + key))
                    .andExpect(jsonPath("$.fileSize").value(104_857_600L))
                    .andExpect(jsonPath("$.mediaType").value("PDF"))
                    .andExpect(jsonPath("$.status").value("AVAILABLE"));

            // size mismatch 가 아니므로 abort 는 절대 호출되지 않아야 함
            then(s3Client).should(never()).abortMultipartUpload(any(AbortMultipartUploadRequest.class));

            // DB 영속화 확인 — fileRepository 가 행을 가지고 있어야 함 (값 동등성 검증)
            assertThat(fileRepository.findAll())
                    .hasSize(1)
                    .first()
                    .satisfies(f -> {
                        assertThat(f.getS3ObjectKey()).isEqualTo(key);
                        assertThat(f.getFilename()).isEqualTo("resume.pdf");
                        assertThat(f.getFileSize()).isEqualTo(104_857_600L);
                        assertThat(f.getMediaType().name()).isEqualTo("PDF");
                    });
        }

        // ============================================================
        // POSITIVE 3 — part-presigned-url (단일 파트 재발급, MD5 옵션 포함)
        // 설계의 5개 엔드포인트 중 part-presigned-url 검증.
        // ============================================================
        @Test
        @DisplayName("part-presigned-url(md5Base64 포함) → 200 + presignedUrl/contentLength/md5Base64 응답 + Presigner 정확 1회 호출")
        void partPresignedUrl_withMd5_returns200_andPassesContentLengthToPresigner() throws Exception {
            String key = "files/pdf/" + UUID.randomUUID() + "__resume.pdf";
            String requestBody = """
                    {
                      "key": "%s",
                      "uploadId": "test-upload-id-12345",
                      "partNumber": 3,
                      "contentLength": 67108864,
                      "md5Base64": "8gWuEUu3Q5Qbd7Q+aXhP+w=="
                    }
                    """.formatted(key);

            mockMvc.perform(post("/api/v1/files/multipart/part-presigned-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.partNumber").value(3))
                    .andExpect(jsonPath("$.presignedUrl").value("https://test-bucket.s3.amazonaws.com/presigned?X-Amz=fake"))
                    .andExpect(jsonPath("$.contentLength").value(67108864))
                    .andExpect(jsonPath("$.md5Base64").value("8gWuEUu3Q5Qbd7Q+aXhP+w=="));

            // 정확히 1회 호출. UploadPartPresignRequest 의 결속을 captor 로 검증
            // (file_security.md §3.4 — Content-Length 결속 의무).
            ArgumentCaptor<UploadPartPresignRequest> presignCaptor =
                    ArgumentCaptor.forClass(UploadPartPresignRequest.class);
            then(s3Presigner).should(times(1)).presignUploadPart(presignCaptor.capture());
            UploadPartPresignRequest captured = presignCaptor.getValue();
            UploadPartRequest sdkUpload = captured.uploadPartRequest();
            assertThat(sdkUpload.bucket()).isEqualTo("test-bucket");
            assertThat(sdkUpload.key()).isEqualTo(key);
            assertThat(sdkUpload.uploadId()).isEqualTo("test-upload-id-12345");
            assertThat(sdkUpload.partNumber()).isEqualTo(3);
            assertThat(sdkUpload.contentLength()).isEqualTo(67108864L);
            // md5Base64 가 제공되었으므로 Content-MD5 도 결속되어야 한다.
            assertThat(sdkUpload.contentMD5()).isEqualTo("8gWuEUu3Q5Qbd7Q+aXhP+w==");
        }

        // ============================================================
        // POSITIVE 4 — list-parts (재개용)
        // 설계의 5개 엔드포인트 중 list-parts 검증.
        // ============================================================
        @Test
        @DisplayName("list-parts(?key&uploadId) → 200 + parts[] 응답 + S3 listParts SDK 정확 1회 호출")
        void listParts_validQuery_returns200_andCallsListPartsOnce() throws Exception {
            String key = "files/pdf/abc__file.pdf";
            // 클라이언트가 1번 파트만 업로드하고 끊긴 시나리오를 모사
            given(s3Client.listParts(any(ListPartsRequest.class)))
                    .willReturn(ListPartsResponse.builder()
                            .parts(java.util.List.of(
                                    Part.builder().partNumber(1).eTag("\"part1-etag\"").size(67108864L).build(),
                                    Part.builder().partNumber(2).eTag("\"part2-etag\"").size(33554432L).build()
                            ))
                            .build());

            mockMvc.perform(get("/api/v1/files/multipart/list-parts")
                            .param("key", key)
                            .param("uploadId", "test-upload-id-12345"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parts.length()").value(2))
                    .andExpect(jsonPath("$.parts[0].partNumber").value(1))
                    .andExpect(jsonPath("$.parts[0].etag").value("\"part1-etag\""))
                    .andExpect(jsonPath("$.parts[0].size").value(67108864))
                    .andExpect(jsonPath("$.parts[1].partNumber").value(2));

            ArgumentCaptor<ListPartsRequest> captor = ArgumentCaptor.forClass(ListPartsRequest.class);
            then(s3Client).should(times(1)).listParts(captor.capture());
            ListPartsRequest captured = captor.getValue();
            assertThat(captured.bucket()).isEqualTo("test-bucket");
            assertThat(captured.key()).isEqualTo(key);
            assertThat(captured.uploadId()).isEqualTo("test-upload-id-12345");
        }
    }

    // ============================================================
    // NEGATIVE 1 — 확장자 화이트리스트 위반
    // ============================================================

    @Nested
    @DisplayName("Negative 시나리오")
    class Negative {

        @Test
        @DisplayName("initiate(.exe) → 400 + error=FILE_EXTENSION_NOT_ALLOWED + S3 SDK 호출 0회 (cheap reject)")
        void initiate_exeExtension_returns400_andDoesNotCallS3() throws Exception {
            String requestBody = """
                    {
                      "originalFileName": "malware.exe",
                      "mimeType": "application/octet-stream",
                      "fileSize": 1024
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("FILE_EXTENSION_NOT_ALLOWED"))
                    .andExpect(jsonPath("$.message").value("허용되지 않는 파일 확장자입니다."));

            // initiate 가 S3 까지 도달하지 않아야 한다 — 정책 검증이 1차로 거부 (file_security.md §3.4).
            then(s3Client).should(never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
            then(s3Presigner).should(never())
                    .presignUploadPart(any(software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest.class));
        }

        // ============================================================
        // NEGATIVE 2 — 100MB 초과
        // ============================================================

        @Test
        @DisplayName("initiate(200MB) → 413 + error=FILE_SIZE_EXCEEDED + S3 SDK 호출 0회")
        void initiate_over100MB_returns413_andDoesNotCallS3() throws Exception {
            // 200MB = 209715200, 한도(104857600) 초과
            String requestBody = """
                    {
                      "originalFileName": "huge.mp4",
                      "mimeType": "video/mp4",
                      "fileSize": 209715200
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(jsonPath("$.error").value("FILE_SIZE_EXCEEDED"))
                    .andExpect(jsonPath("$.message").value("파일 크기가 허용된 한도를 초과했습니다."));

            then(s3Client).should(never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
            then(s3Presigner).should(never())
                    .presignUploadPart(any(software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest.class));
        }

        // ============================================================
        // NEGATIVE 3 — complete 단계 사후 크기 검증 실패 → abort 자동 호출
        // ============================================================

        @Test
        @DisplayName("complete(headObject 가 약속과 다른 size 반환) → 400 + error=FILE_SIZE_MISMATCH + abortMultipartUpload 정확 1회 호출 + DB 영속화 0회")
        void complete_actualSizeDiffersFromPromised_returns400_andCallsAbortOnce() throws Exception {
            String key = "files/pdf/" + UUID.randomUUID() + "__resume.pdf";

            // 클라이언트는 100MB 라고 주장하지만 S3 가 실제로는 200MB 라고 보고하는 시나리오 (DoS 우회 시도).
            given(s3Client.headObject(any(HeadObjectRequest.class)))
                    .willReturn(HeadObjectResponse.builder().contentLength(209_715_200L).build());

            String requestBody = """
                    {
                      "key": "%s",
                      "uploadId": "test-upload-id-12345",
                      "parts": [
                        { "partNumber": 1, "etag": "\\"etag-1\\"" }
                      ],
                      "originalFileName": "resume.pdf",
                      "fileSize": 104857600,
                      "mimeType": "application/pdf"
                    }
                    """.formatted(key);

            mockMvc.perform(post("/api/v1/files/multipart/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("FILE_SIZE_MISMATCH"))
                    .andExpect(jsonPath("$.message").value("업로드된 파일 크기가 약속한 크기와 일치하지 않습니다."));

            // 사후 검증 실패 → orphan 방지를 위해 abort 정확히 1회 호출되어야 한다 (file_security.md §3.4 + §7.2).
            ArgumentCaptor<AbortMultipartUploadRequest> abortCaptor =
                    ArgumentCaptor.forClass(AbortMultipartUploadRequest.class);
            then(s3Client).should(times(1)).abortMultipartUpload(abortCaptor.capture());
            AbortMultipartUploadRequest abortReq = abortCaptor.getValue();
            assertThat(abortReq.bucket()).isEqualTo("test-bucket");
            assertThat(abortReq.key()).isEqualTo(key);
            assertThat(abortReq.uploadId()).isEqualTo("test-upload-id-12345");

            // DB 영속화는 발생하지 않아야 함 (트랜잭션 자체가 throw 로 롤백)
            assertThat(fileRepository.findAll()).isEmpty();
        }

        // ============================================================
        // NEGATIVE 4 — 명시적 abort
        // ============================================================

        @Test
        @DisplayName("abort(정상 입력) → 204 + 본문 없음 + abortMultipartUpload SDK 정확 1회 호출")
        void abort_validRequest_returns204_andCallsAbortOnce() throws Exception {
            String requestBody = """
                    {
                      "key": "files/pdf/abc-uuid__file.pdf",
                      "uploadId": "test-upload-id-12345"
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/abort")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            ArgumentCaptor<AbortMultipartUploadRequest> abortCaptor =
                    ArgumentCaptor.forClass(AbortMultipartUploadRequest.class);
            then(s3Client).should(times(1)).abortMultipartUpload(abortCaptor.capture());
            AbortMultipartUploadRequest captured = abortCaptor.getValue();
            assertThat(captured.bucket()).isEqualTo("test-bucket");
            assertThat(captured.key()).isEqualTo("files/pdf/abc-uuid__file.pdf");
            assertThat(captured.uploadId()).isEqualTo("test-upload-id-12345");
        }
    }
}
