package com.capstone.passfolio.domain.file.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.passfolio.domain.file.dto.FileDto;
import com.capstone.passfolio.domain.file.entity.File;
import com.capstone.passfolio.domain.file.entity.enums.ActionType;
import com.capstone.passfolio.domain.file.entity.enums.DocumentType;
import com.capstone.passfolio.domain.file.entity.enums.MediaType;
import com.capstone.passfolio.domain.file.service.FileService;
import com.capstone.passfolio.system.exception.handler.GlobalExceptionHandler;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link FileController} 슬라이스 테스트.
 *
 * <p>설계 참조: {@code 0001-harness-design.md} Tasks T19 + Acceptance signals 의 controller 행
 * (e.g. {@code FileControllerTest.*} 표시 케이스).
 *
 * <p><b>테스트 범위 (검증 목적 우선)</b> — springboot-test 스킬 §0:
 * <ul>
 *   <li>{@code @Valid} 어노테이션이 실제로 작동해 잘못된 입력을 400 으로 거부하는지 (controller 가
 *       서비스에 잘못된 입력을 절대 전달하지 않는다는 명세).</li>
 *   <li>5개 endpoint 가 모두 올바른 path/verb/상태코드 (200/204) 로 매핑되어 있는지
 *       (URL 변경/HTTP verb 회귀 차단).</li>
 *   <li>controller 가 입력 DTO 를 그대로 (변형/누락 없이) FileService 로 위임하는지
 *       (ArgumentCaptor 로 captured DTO field 단위 검증 — controller 의 thin-mapper 책임).</li>
 *   <li>응답 JSON 의 shape (필드명/타입/값) 이 design §"Interface contracts" 와 정확히 일치하는지
 *       (클라이언트 contract 회귀 차단).</li>
 *   <li>{@code FileService.completeMultipartUpload} 가 throw 한 {@link RestException} 이
 *       글로벌 핸들러 ({@link GlobalExceptionHandler}) 를 통해 표준 ErrorResponse 로 직렬화되는지
 *       (예외→HTTP 매핑 contract).</li>
 * </ul>
 *
 * <p><b>제외 (다른 테스트가 다룸)</b>:
 * <ul>
 *   <li>업로드 정책 (확장자 / 크기) 검증 자체 → T18 {@code FileServiceTest} 가 다룸.</li>
 *   <li>S3 SDK 호출 / presigned URL 형식 → T17 {@code S3ServiceTest} 가 다룸.</li>
 *   <li>실제 cdn URL 결합 → T13/T04 가 단위로 검증.</li>
 *   <li>전체 happy-path E2E (Spring 컨텍스트 + 모든 빈 + Security 필터) → T21 {@code FileUploadE2ETest}.</li>
 * </ul>
 *
 * <p><b>Security</b>: 본 백로그의 controller 는 auth-agnostic ({@link FileController} 결정 §"No JWT/auth gate")
 * 이므로 보안 필터를 비활성 ({@code addFilters = false}) — happy/error 본문 자체에 집중. 향후 auth wiring
 * 백로그가 추가되면 별도 테스트가 그 보안 layer 를 검증한다.
 *
 * <p><b>Skill 적용</b>: {@code springboot-test} (§3 — Slice: Controller). 핵심 골격 채택:
 * {@code @WebMvcTest(FileController.class)} + {@code @MockitoBean FileService} ({@code @MockBean} deprecated)
 * + Bean Validation 실패 케이스 명시 (happy path 만 두지 말 것 §3 규칙).
 */
/*
 * 메인 {@code @SpringBootApplication} 클래스에 {@code @EnableJpaAuditing} 이 있어, 기본
 * {@code @WebMvcTest} 슬라이스가 JPA 컨텍스트를 시도해 로드한다. 이 슬라이스는 의도적으로
 * controller 직렬화/검증/예외처리만 검증하므로 (서비스/리포지토리는 mock 처리) JPA
 * 메타모델이 비어 {@code "JPA metamodel must not be empty"} 로 컨텍스트 로딩이 실패한다.
 * 따라서 슬라이스에서 JPA/DataSource 자동구성을 명시적으로 제외한다 — controller 책임 검증과
 * 무관한 인프라 의존성이므로.
 */
@WebMvcTest(
        controllers = FileController.class,
        excludeAutoConfiguration = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                JpaRepositoriesAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    /**
     * 메인 {@code @SpringBootApplication} 의 {@code @EnableJpaAuditing} 이 등록한
     * {@code jpaAuditingHandler} 빈은 {@code jpaMappingContext} 를 의존한다. 이 슬라이스는
     * JPA 메타모델 자체가 비어 있어 실제 {@link JpaMetamodelMappingContext} 가 만들어질 수 없으므로
     * mock 으로 채워 컨텍스트 로딩 자체를 통과시킨다 (controller 슬라이스는 JPA 동작을 검증하지 않음).
     */
    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    /**
     * {@link com.capstone.passfolio.common.util.FileUrlUtils} 는 {@code @Value} setter 로 정적 필드를
     * 채우는 패턴이라 {@code @WebMvcTest} 슬라이스에서는 자동 주입되지 않는다 ({@code cdn.base-url}
     * 프로퍼티가 슬라이스 컨텍스트에 안 올라옴). complete 응답 JSON 의 {@code cdnUrl} 필드를 검증하려면
     * 정적 필드를 직접 세팅해야 하므로 {@code @BeforeAll} 에서 한 번 주입하고 {@code @AfterAll} 에서 정리.
     */
    @BeforeAll
    static void setupCdnBaseUrl() {
        ReflectionTestUtils.setField(
                com.capstone.passfolio.common.util.FileUrlUtils.class,
                "cdnBaseUrl",
                "https://cdn.passfolio.test");
    }

    @AfterAll
    static void resetCdnBaseUrl() {
        ReflectionTestUtils.setField(
                com.capstone.passfolio.common.util.FileUrlUtils.class,
                "cdnBaseUrl",
                null);
    }

    // ============================================================
    // 1) POST /api/v1/files/multipart/initiate
    // ============================================================

    @Nested
    @DisplayName("POST /api/v1/files/multipart/initiate")
    class InitiateMultipartUpload {

        @Test
        @DisplayName("happy path: 200 + 응답 JSON shape 가 design §Interface contracts 와 일치 + 입력이 그대로 서비스에 위임된다")
        void initiate_happyPath_returns200WithExpectedJsonShape() throws Exception {
            // given — 서비스가 design 의 응답 스키마대로 DTO 를 돌려준다고 가정
            FileDto.MultipartUploadInitiateResponse svcResponse =
                    FileDto.MultipartUploadInitiateResponse.builder()
                            .key("files/pdf/3f2d04ec-12ab-4cde-9abc-1234567890ab__resume.pdf")
                            .uploadId("upload-id-12345")
                            .contentType("application/pdf")
                            .partCount(2)
                            .partPresignedUrls(List.of(
                                    FileDto.PartPresignedUrl.builder()
                                            .partNumber(1)
                                            .presignedUrl("https://s3.example.com/part1?X-Amz=...")
                                            .contentLength(67_108_864L)
                                            .build(),
                                    FileDto.PartPresignedUrl.builder()
                                            .partNumber(2)
                                            .presignedUrl("https://s3.example.com/part2?X-Amz=...")
                                            .contentLength(37_748_736L)
                                            .build()))
                            .build();
            given(fileService.initiateMultipartUpload(any(FileDto.MultipartUploadInitiateRequest.class)))
                    .willReturn(svcResponse);

            String requestBody = """
                    {
                      "originalFileName": "resume.pdf",
                      "mimeType": "application/pdf",
                      "fileSize": 104857600
                    }
                    """;

            // when / then — 응답 JSON 의 모든 필드를 명시 검증 (springboot-test §0: isNotNull X)
            mockMvc.perform(post("/api/v1/files/multipart/initiate")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.key").value("files/pdf/3f2d04ec-12ab-4cde-9abc-1234567890ab__resume.pdf"))
                    .andExpect(jsonPath("$.uploadId").value("upload-id-12345"))
                    .andExpect(jsonPath("$.contentType").value("application/pdf"))
                    .andExpect(jsonPath("$.partCount").value(2))
                    .andExpect(jsonPath("$.partPresignedUrls.length()").value(2))
                    .andExpect(jsonPath("$.partPresignedUrls[0].partNumber").value(1))
                    .andExpect(jsonPath("$.partPresignedUrls[0].presignedUrl").value("https://s3.example.com/part1?X-Amz=..."))
                    .andExpect(jsonPath("$.partPresignedUrls[0].contentLength").value(67_108_864L))
                    .andExpect(jsonPath("$.partPresignedUrls[1].partNumber").value(2))
                    .andExpect(jsonPath("$.partPresignedUrls[1].contentLength").value(37_748_736L));

            // 위임 contract — controller 가 입력 DTO 를 변형 없이 서비스에 넘겼는지 확인
            ArgumentCaptor<FileDto.MultipartUploadInitiateRequest> captor =
                    ArgumentCaptor.forClass(FileDto.MultipartUploadInitiateRequest.class);
            then(fileService).should().initiateMultipartUpload(captor.capture());
            FileDto.MultipartUploadInitiateRequest captured = captor.getValue();
            assertThat(captured.getOriginalFileName()).isEqualTo("resume.pdf");
            assertThat(captured.getMimeType()).isEqualTo("application/pdf");
            assertThat(captured.getFileSize()).isEqualTo(104_857_600L);
        }

        @Test
        @DisplayName("@Valid 위반: originalFileName 이 비면 400, 서비스 호출 X")
        void initiate_blankOriginalFileName_returns400_andDoesNotCallService() throws Exception {
            String requestBody = """
                    {
                      "originalFileName": "",
                      "mimeType": "application/pdf",
                      "fileSize": 1024
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/initiate")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            // 정책 검증 (FileService.validateUploadPolicy) 까지 도달하지 못해야 한다 — @Valid 가 1차 차단.
            then(fileService).should(never()).initiateMultipartUpload(any());
        }

        @Test
        @DisplayName("@Valid 위반: originalFileName 누락 → 400, 서비스 호출 X")
        void initiate_missingOriginalFileName_returns400_andDoesNotCallService() throws Exception {
            String requestBody = """
                    {
                      "mimeType": "application/pdf",
                      "fileSize": 1024
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/initiate")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).initiateMultipartUpload(any());
        }

        @Test
        @DisplayName("@Valid 위반: fileSize 누락 → 400, 서비스 호출 X")
        void initiate_missingFileSize_returns400_andDoesNotCallService() throws Exception {
            String requestBody = """
                    {
                      "originalFileName": "resume.pdf",
                      "mimeType": "application/pdf"
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/initiate")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).initiateMultipartUpload(any());
        }

        @Test
        @DisplayName("@Valid 위반: fileSize=0 → @Min(1) 위반 → 400, 서비스 호출 X")
        void initiate_zeroFileSize_returns400_andDoesNotCallService() throws Exception {
            String requestBody = """
                    {
                      "originalFileName": "resume.pdf",
                      "fileSize": 0
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/initiate")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).initiateMultipartUpload(any());
        }

        @Test
        @DisplayName("정상 입력 + 서비스가 RestException(FILE_EXTENSION_NOT_ALLOWED) → 400 + 표준 ErrorResponse 본문")
        void initiate_serviceRejectsExtension_returns400WithErrorCode() throws Exception {
            given(fileService.initiateMultipartUpload(any(FileDto.MultipartUploadInitiateRequest.class)))
                    .willThrow(new RestException(ErrorCode.FILE_EXTENSION_NOT_ALLOWED));

            String requestBody = """
                    {
                      "originalFileName": "malware.exe",
                      "fileSize": 1024
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/initiate")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("FILE_EXTENSION_NOT_ALLOWED"))
                    .andExpect(jsonPath("$.message").value("허용되지 않는 파일 확장자입니다."));
        }

        @Test
        @DisplayName("정상 입력 + 서비스가 RestException(FILE_SIZE_EXCEEDED) → 413 + 표준 ErrorResponse 본문")
        void initiate_serviceRejectsSize_returns413WithErrorCode() throws Exception {
            given(fileService.initiateMultipartUpload(any(FileDto.MultipartUploadInitiateRequest.class)))
                    .willThrow(new RestException(ErrorCode.FILE_SIZE_EXCEEDED));

            String requestBody = """
                    {
                      "originalFileName": "huge.mp4",
                      "fileSize": 209715200
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/initiate")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(jsonPath("$.error").value("FILE_SIZE_EXCEEDED"));
        }
    }

    // ============================================================
    // 2) POST /api/v1/files/multipart/part-presigned-url
    // ============================================================

    @Nested
    @DisplayName("POST /api/v1/files/multipart/part-presigned-url")
    class GeneratePartPresignedUrl {

        @Test
        @DisplayName("happy path: 200 + 응답 JSON shape + 입력 DTO 가 그대로 위임된다")
        void partPresignedUrl_happyPath_returns200WithExpectedJsonShape() throws Exception {
            FileDto.PartPresignedUrlSingleResponse svcResponse =
                    FileDto.PartPresignedUrlSingleResponse.builder()
                            .partNumber(7)
                            .presignedUrl("https://s3.example.com/part7?X-Amz=...")
                            .contentLength(67_108_864L)
                            .md5Base64("8gWuEUu3Q5Qbd7Q+aXhP+w==")
                            .build();
            given(fileService.generatePartPresignedUrl(any(FileDto.PartPresignedUrlSingleRequest.class)))
                    .willReturn(svcResponse);

            String requestBody = """
                    {
                      "key": "files/pdf/uuid__file.pdf",
                      "uploadId": "upload-id-12345",
                      "partNumber": 7,
                      "contentLength": 67108864,
                      "md5Base64": "8gWuEUu3Q5Qbd7Q+aXhP+w=="
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/part-presigned-url")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.partNumber").value(7))
                    .andExpect(jsonPath("$.presignedUrl").value("https://s3.example.com/part7?X-Amz=..."))
                    .andExpect(jsonPath("$.contentLength").value(67_108_864L))
                    .andExpect(jsonPath("$.md5Base64").value("8gWuEUu3Q5Qbd7Q+aXhP+w=="));

            ArgumentCaptor<FileDto.PartPresignedUrlSingleRequest> captor =
                    ArgumentCaptor.forClass(FileDto.PartPresignedUrlSingleRequest.class);
            then(fileService).should().generatePartPresignedUrl(captor.capture());
            FileDto.PartPresignedUrlSingleRequest captured = captor.getValue();
            assertThat(captured.getKey()).isEqualTo("files/pdf/uuid__file.pdf");
            assertThat(captured.getUploadId()).isEqualTo("upload-id-12345");
            assertThat(captured.getPartNumber()).isEqualTo(7);
            assertThat(captured.getContentLength()).isEqualTo(67_108_864L);
            assertThat(captured.getMd5Base64()).isEqualTo("8gWuEUu3Q5Qbd7Q+aXhP+w==");
        }

        @Test
        @DisplayName("md5Base64 누락은 OK (옵션 필드) — 서비스 위임 시 null 로 전달")
        void partPresignedUrl_omitsMd5_isAcceptedAndPassedAsNull() throws Exception {
            FileDto.PartPresignedUrlSingleResponse svcResponse =
                    FileDto.PartPresignedUrlSingleResponse.builder()
                            .partNumber(1)
                            .presignedUrl("https://s3.example.com/part1?X-Amz=...")
                            .contentLength(67_108_864L)
                            .md5Base64(null)
                            .build();
            given(fileService.generatePartPresignedUrl(any(FileDto.PartPresignedUrlSingleRequest.class)))
                    .willReturn(svcResponse);

            String requestBody = """
                    {
                      "key": "files/pdf/uuid__file.pdf",
                      "uploadId": "upload-id-12345",
                      "partNumber": 1,
                      "contentLength": 67108864
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/part-presigned-url")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.md5Base64").doesNotExist());

            ArgumentCaptor<FileDto.PartPresignedUrlSingleRequest> captor =
                    ArgumentCaptor.forClass(FileDto.PartPresignedUrlSingleRequest.class);
            then(fileService).should().generatePartPresignedUrl(captor.capture());
            assertThat(captor.getValue().getMd5Base64()).isNull();
        }

        @Test
        @DisplayName("@Valid 위반: key blank → 400, 서비스 호출 X")
        void partPresignedUrl_blankKey_returns400_andDoesNotCallService() throws Exception {
            String requestBody = """
                    {
                      "key": "",
                      "uploadId": "upload-id-12345",
                      "partNumber": 1,
                      "contentLength": 1024
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/part-presigned-url")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).generatePartPresignedUrl(any());
        }

        @Test
        @DisplayName("@Valid 위반: partNumber=0 (@Min(1)) → 400, 서비스 호출 X")
        void partPresignedUrl_partNumberZero_returns400_andDoesNotCallService() throws Exception {
            String requestBody = """
                    {
                      "key": "files/pdf/uuid__x.pdf",
                      "uploadId": "u-id",
                      "partNumber": 0,
                      "contentLength": 1024
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/part-presigned-url")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).generatePartPresignedUrl(any());
        }

        @Test
        @DisplayName("@Valid 위반: partNumber=10001 (@Max(10000)) → 400, 서비스 호출 X")
        void partPresignedUrl_partNumberOver10000_returns400_andDoesNotCallService() throws Exception {
            String requestBody = """
                    {
                      "key": "files/pdf/uuid__x.pdf",
                      "uploadId": "u-id",
                      "partNumber": 10001,
                      "contentLength": 1024
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/part-presigned-url")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).generatePartPresignedUrl(any());
        }

        @Test
        @DisplayName("@Valid 위반: contentLength 누락 → 400, 서비스 호출 X")
        void partPresignedUrl_missingContentLength_returns400_andDoesNotCallService() throws Exception {
            String requestBody = """
                    {
                      "key": "files/pdf/uuid__x.pdf",
                      "uploadId": "u-id",
                      "partNumber": 1
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/part-presigned-url")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).generatePartPresignedUrl(any());
        }
    }

    // ============================================================
    // 3) GET /api/v1/files/multipart/list-parts
    // ============================================================

    @Nested
    @DisplayName("GET /api/v1/files/multipart/list-parts")
    class ListUploadedParts {

        @Test
        @DisplayName("happy path: 200 + parts 배열 JSON shape + key/uploadId 가 그대로 위임된다")
        void listParts_happyPath_returns200WithExpectedJsonShape() throws Exception {
            FileDto.ListPartsResponse svcResponse = FileDto.ListPartsResponse.builder()
                    .parts(List.of(
                            FileDto.ListPartsResponse.UploadedPart.builder()
                                    .partNumber(1)
                                    .etag("\"etag-1\"")
                                    .size(67_108_864L)
                                    .build(),
                            FileDto.ListPartsResponse.UploadedPart.builder()
                                    .partNumber(2)
                                    .etag("\"etag-2\"")
                                    .size(37_748_736L)
                                    .build()))
                    .build();
            given(fileService.listUploadedParts(eq("files/uuid__x.pdf"), eq("upload-id-12345")))
                    .willReturn(svcResponse);

            mockMvc.perform(get("/api/v1/files/multipart/list-parts")
                            .param("key", "files/uuid__x.pdf")
                            .param("uploadId", "upload-id-12345"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parts.length()").value(2))
                    .andExpect(jsonPath("$.parts[0].partNumber").value(1))
                    .andExpect(jsonPath("$.parts[0].etag").value("\"etag-1\""))
                    .andExpect(jsonPath("$.parts[0].size").value(67_108_864L))
                    .andExpect(jsonPath("$.parts[1].partNumber").value(2))
                    .andExpect(jsonPath("$.parts[1].size").value(37_748_736L));

            then(fileService).should().listUploadedParts(eq("files/uuid__x.pdf"), eq("upload-id-12345"));
        }

        @Test
        @DisplayName("required @RequestParam key 누락 → 400, 서비스 호출 X")
        void listParts_missingKey_returns400_andDoesNotCallService() throws Exception {
            mockMvc.perform(get("/api/v1/files/multipart/list-parts")
                            .param("uploadId", "upload-id-12345"))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).listUploadedParts(anyString(), anyString());
        }

        @Test
        @DisplayName("required @RequestParam uploadId 누락 → 400, 서비스 호출 X")
        void listParts_missingUploadId_returns400_andDoesNotCallService() throws Exception {
            mockMvc.perform(get("/api/v1/files/multipart/list-parts")
                            .param("key", "files/uuid__x.pdf"))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).listUploadedParts(anyString(), anyString());
        }
    }

    // ============================================================
    // 4) POST /api/v1/files/multipart/complete
    // ============================================================

    @Nested
    @DisplayName("POST /api/v1/files/multipart/complete")
    class CompleteMultipartUpload {

        @Test
        @DisplayName("happy path: 200 + UploadFileResponse JSON shape (cdnUrl 포함) + 입력 DTO 위임")
        void complete_happyPath_returns200WithUploadFileResponse() throws Exception {
            // given — 영속화된 File 엔티티 (id 포함). UploadFileResponse.of(File) 가 cdnUrl 을 결합.
            File savedFile = File.builder()
                    .id(42L)
                    .s3ObjectKey("files/uuid__resume.pdf")
                    .filename("resume.pdf")
                    .fileSize(104_857_600L)
                    .mediaType(MediaType.PDF)
                    .build();
            given(fileService.completeMultipartUpload(any(FileDto.CompleteMultipartUploadRequest.class)))
                    .willReturn(savedFile);

            String requestBody = """
                    {
                      "key": "files/uuid__resume.pdf",
                      "uploadId": "upload-id-12345",
                      "parts": [
                        { "partNumber": 1, "etag": "\\"etag-1\\"" },
                        { "partNumber": 2, "etag": "\\"etag-2\\"" }
                      ],
                      "originalFileName": "resume.pdf",
                      "fileSize": 104857600,
                      "mimeType": "application/pdf"
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/complete")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileId").value(42))
                    .andExpect(jsonPath("$.filename").value("resume.pdf"))
                    .andExpect(jsonPath("$.cdnUrl").value("https://cdn.passfolio.test/files/uuid__resume.pdf"))
                    .andExpect(jsonPath("$.fileSize").value(104_857_600L))
                    .andExpect(jsonPath("$.mediaType").value("PDF"))
                    .andExpect(jsonPath("$.status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.documentType").value(nullValue()))
                    .andExpect(jsonPath("$.actionType").value(nullValue()));

            // 위임 contract — controller 가 입력 body 를 그대로 서비스에 넘겼는지 확인
            ArgumentCaptor<FileDto.CompleteMultipartUploadRequest> captor =
                    ArgumentCaptor.forClass(FileDto.CompleteMultipartUploadRequest.class);
            then(fileService).should().completeMultipartUpload(captor.capture());
            FileDto.CompleteMultipartUploadRequest captured = captor.getValue();
            assertThat(captured.getKey()).isEqualTo("files/uuid__resume.pdf");
            assertThat(captured.getUploadId()).isEqualTo("upload-id-12345");
            assertThat(captured.getOriginalFileName()).isEqualTo("resume.pdf");
            assertThat(captured.getFileSize()).isEqualTo(104_857_600L);
            assertThat(captured.getMimeType()).isEqualTo("application/pdf");
            assertThat(captured.getParts()).hasSize(2);
            assertThat(captured.getParts().get(0).getPartNumber()).isEqualTo(1);
            assertThat(captured.getParts().get(0).getEtag()).isEqualTo("\"etag-1\"");
            assertThat(captured.getParts().get(1).getPartNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("@Valid 위반: parts 빈 배열 (@NotEmpty) → 400, 서비스 호출 X")
        void complete_emptyParts_returns400_andDoesNotCallService() throws Exception {
            String requestBody = """
                    {
                      "key": "files/pdf/uuid__x.pdf",
                      "uploadId": "u-id",
                      "parts": [],
                      "originalFileName": "x.pdf",
                      "fileSize": 1024
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/complete")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).completeMultipartUpload(any());
        }

        @Test
        @DisplayName("@Valid 위반: originalFileName blank → 400, 서비스 호출 X")
        void complete_blankOriginalFileName_returns400_andDoesNotCallService() throws Exception {
            String requestBody = """
                    {
                      "key": "files/pdf/uuid__x.pdf",
                      "uploadId": "u-id",
                      "parts": [{ "partNumber": 1, "etag": "\\"e\\"" }],
                      "originalFileName": "",
                      "fileSize": 1024
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/complete")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).completeMultipartUpload(any());
        }

        @Test
        @DisplayName("@Valid 위반: fileSize 누락 → 400, 서비스 호출 X")
        void complete_missingFileSize_returns400_andDoesNotCallService() throws Exception {
            String requestBody = """
                    {
                      "key": "files/pdf/uuid__x.pdf",
                      "uploadId": "u-id",
                      "parts": [{ "partNumber": 1, "etag": "\\"e\\"" }],
                      "originalFileName": "x.pdf"
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/complete")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).completeMultipartUpload(any());
        }

        @Test
        @DisplayName("서비스가 RestException(FILE_SIZE_MISMATCH) → 400 + 표준 ErrorResponse 본문")
        void complete_serviceDetectsSizeMismatch_returns400WithErrorCode() throws Exception {
            willThrow(new RestException(ErrorCode.FILE_SIZE_MISMATCH))
                    .given(fileService).completeMultipartUpload(any(FileDto.CompleteMultipartUploadRequest.class));

            String requestBody = """
                    {
                      "key": "files/pdf/uuid__x.pdf",
                      "uploadId": "u-id",
                      "parts": [{ "partNumber": 1, "etag": "\\"e\\"" }],
                      "originalFileName": "x.pdf",
                      "fileSize": 1024
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/complete")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("FILE_SIZE_MISMATCH"))
                    .andExpect(jsonPath("$.message").value("업로드된 파일 크기가 약속한 크기와 일치하지 않습니다."));
        }

        @Test
        @DisplayName("documentType + actionType 바인딩 + 응답 노출 + 서비스에 그대로 위임")
        void complete_bindsAndExposesDocumentTypeAndActionType() throws Exception {
            File savedFile = File.builder()
                    .id(7L)
                    .s3ObjectKey("files/uuid__portfolio.pdf")
                    .filename("portfolio.pdf")
                    .fileSize(1024L)
                    .mediaType(MediaType.PDF)
                    .documentType(DocumentType.PORTFOLIO)
                    .actionType(ActionType.GENERATE)
                    .build();
            given(fileService.completeMultipartUpload(any(FileDto.CompleteMultipartUploadRequest.class)))
                    .willReturn(savedFile);

            String requestBody = """
                    {
                      "key": "files/uuid__portfolio.pdf",
                      "uploadId": "u-id",
                      "parts": [{ "partNumber": 1, "etag": "\\"e\\"" }],
                      "originalFileName": "portfolio.pdf",
                      "fileSize": 1024,
                      "documentType": "PORTFOLIO",
                      "actionType": "GENERATE"
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/complete")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documentType").value("PORTFOLIO"))
                    .andExpect(jsonPath("$.actionType").value("GENERATE"));

            ArgumentCaptor<FileDto.CompleteMultipartUploadRequest> captor =
                    ArgumentCaptor.forClass(FileDto.CompleteMultipartUploadRequest.class);
            then(fileService).should().completeMultipartUpload(captor.capture());
            assertThat(captor.getValue().getDocumentType()).isEqualTo(DocumentType.PORTFOLIO);
            assertThat(captor.getValue().getActionType()).isEqualTo(ActionType.GENERATE);
        }

        @Test
        @DisplayName("documentType + actionType 미포함 JSON → 200 + 두 필드 null 위임 + 응답에도 null 노출")
        void complete_acceptsNullDocumentTypeAndActionType() throws Exception {
            File savedFile = File.builder()
                    .id(8L)
                    .s3ObjectKey("files/uuid__x.pdf")
                    .filename("x.pdf")
                    .fileSize(1024L)
                    .mediaType(MediaType.PDF)
                    .build();
            given(fileService.completeMultipartUpload(any(FileDto.CompleteMultipartUploadRequest.class)))
                    .willReturn(savedFile);

            String requestBody = """
                    {
                      "key": "files/uuid__x.pdf",
                      "uploadId": "u-id",
                      "parts": [{ "partNumber": 1, "etag": "\\"e\\"" }],
                      "originalFileName": "x.pdf",
                      "fileSize": 1024
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/complete")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documentType").value(nullValue()))
                    .andExpect(jsonPath("$.actionType").value(nullValue()));

            ArgumentCaptor<FileDto.CompleteMultipartUploadRequest> captor =
                    ArgumentCaptor.forClass(FileDto.CompleteMultipartUploadRequest.class);
            then(fileService).should().completeMultipartUpload(captor.capture());
            assertThat(captor.getValue().getDocumentType()).isNull();
            assertThat(captor.getValue().getActionType()).isNull();
        }

        @Test
        @DisplayName("documentType 에 정의되지 않은 enum 문자열 → 400 + error=JSON_PARSE_ERROR, 서비스 호출 X")
        void complete_invalidDocumentTypeEnum_returns400_withJsonParseErrorCode() throws Exception {
            String requestBody = """
                    {
                      "key": "files/uuid__x.pdf",
                      "uploadId": "u-id",
                      "parts": [{ "partNumber": 1, "etag": "\\"e\\"" }],
                      "originalFileName": "x.pdf",
                      "fileSize": 1024,
                      "documentType": "INVALID_VALUE"
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/complete")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("JSON_PARSE_ERROR"));

            then(fileService).should(never()).completeMultipartUpload(any());
        }

        @Test
        @DisplayName("actionType 에 정의되지 않은 enum 문자열 → 400 + error=JSON_PARSE_ERROR, 서비스 호출 X")
        void complete_invalidActionTypeEnum_returns400_withJsonParseErrorCode() throws Exception {
            String requestBody = """
                    {
                      "key": "files/uuid__x.pdf",
                      "uploadId": "u-id",
                      "parts": [{ "partNumber": 1, "etag": "\\"e\\"" }],
                      "originalFileName": "x.pdf",
                      "fileSize": 1024,
                      "actionType": "DELETE"
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/complete")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("JSON_PARSE_ERROR"));

            then(fileService).should(never()).completeMultipartUpload(any());
        }
    }

    // ============================================================
    // 5) POST /api/v1/files/multipart/abort
    // ============================================================

    @Nested
    @DisplayName("POST /api/v1/files/multipart/abort")
    class AbortMultipartUpload {

        @Test
        @DisplayName("happy path: 204 No Content + 본문 비어있음 + key/uploadId 가 그대로 위임된다")
        void abort_happyPath_returns204_andDelegatesToService() throws Exception {
            // 서비스 메서드는 void — willReturn 불필요. 정상 종료만 가정.

            String requestBody = """
                    {
                      "key": "files/pdf/uuid__x.pdf",
                      "uploadId": "upload-id-12345"
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/abort")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));   // 204 No Content → 본문 없어야 함

            then(fileService).should().abortMultipartUpload(eq("files/pdf/uuid__x.pdf"), eq("upload-id-12345"));
        }

        @Test
        @DisplayName("@Valid 위반: key blank → 400, 서비스 호출 X")
        void abort_blankKey_returns400_andDoesNotCallService() throws Exception {
            String requestBody = """
                    {
                      "key": "",
                      "uploadId": "upload-id-12345"
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/abort")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).abortMultipartUpload(anyString(), anyString());
        }

        @Test
        @DisplayName("@Valid 위반: uploadId blank → 400, 서비스 호출 X")
        void abort_blankUploadId_returns400_andDoesNotCallService() throws Exception {
            String requestBody = """
                    {
                      "key": "files/pdf/uuid__x.pdf",
                      "uploadId": ""
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/abort")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            then(fileService).should(never()).abortMultipartUpload(anyString(), anyString());
        }

        @Test
        @DisplayName("정상 입력 + 서비스가 RestException(FILE_UPLOAD_S3_ERROR) → 502 + 표준 ErrorResponse 본문")
        void abort_serviceFailsOnS3_returns502WithErrorCode() throws Exception {
            willThrow(new RestException(ErrorCode.FILE_UPLOAD_S3_ERROR))
                    .given(fileService).abortMultipartUpload(anyString(), anyString());

            String requestBody = """
                    {
                      "key": "files/pdf/uuid__x.pdf",
                      "uploadId": "upload-id-12345"
                    }
                    """;

            mockMvc.perform(post("/api/v1/files/multipart/abort")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error").value("FILE_UPLOAD_S3_ERROR"));
        }
    }
}
