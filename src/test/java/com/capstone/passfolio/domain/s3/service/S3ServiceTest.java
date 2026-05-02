package com.capstone.passfolio.domain.s3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.capstone.passfolio.domain.file.entity.enums.MediaType;
import com.capstone.passfolio.domain.s3.dto.S3ServiceDto;
import com.capstone.passfolio.system.config.file.FileUploadProperties;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

/**
 * {@link S3Service} 단위 테스트.
 *
 * <p>Spring 컨텍스트 0개. {@link S3Client} 와 {@link S3Presigner} 는 Mockito mock 으로 주입하며,
 * {@code @Value("${spring.cloud.aws.s3.bucket}")} 로 주입되는 {@code bucketName} 은
 * {@link ReflectionTestUtils#setField} 로 직접 세팅한다.
 *
 * <p>검증 명세 (설계 §"Acceptance signals"):
 * <ol>
 *   <li>{@code initiateUpload}: {@code CreateMultipartUploadRequest.bucket/key/contentType} 정확.
 *       key 가 {@code (files|videos)/UUID__sanitized} 패턴 + 경로 traversal 무력화.</li>
 *   <li>{@code generatePresignedUrl}: {@code Content-Length} 가 양수일 때 항상 결속,
 *       {@code Content-MD5} 는 클라이언트가 보낼 때만 결속, TTL 은 properties 값.</li>
 *   <li>{@code generatePartPresignedUrls}: fileSize → partCount 만큼 발급, 마지막 part 는 잔여 바이트.</li>
 *   <li>{@code completeUpload}: 입력이 역순/뒤섞여도 SDK 호출 시 {@code partNumber} 오름차순 정렬.</li>
 *   <li>{@code abortUpload}: SDK {@code abortMultipartUpload} 정확히 1회 + 인자 전달.</li>
 *   <li>{@code listUploadedParts}: SDK 응답을 DTO 로 정확히 매핑.</li>
 *   <li>{@code getObjectActualSize}: {@code HeadObjectResponse.contentLength} 그대로 반환.</li>
 *   <li>{@code calculatePartCount}: 50MB → 1, 100MB → 2 (백로그 한도 경계),
 *       null/0 → 1, 음수 → 1.</li>
 *   <li>{@code calculatePartSize}: 1 part → fileSize, n part → 마지막 잔여.</li>
 *   <li>subfolder: {@link MediaType#VIDEO}/{@link MediaType#AUDIO} → "videos/", 나머지/null fallback → "files/" 또는 mime/확장자 기반.</li>
 * </ol>
 *
 * <p>각 테스트는 SUT 의 어떤 동작이 깨졌는지를 한 줄로 설명할 수 있어야 한다 — {@code isNotNull()} 단독 검증 금지.
 */
@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    private static final String BUCKET = "test-bucket";
    private static final int TTL_MIN = 10;
    private static final long MB = 1024L * 1024;

    /** ^(files|videos)/[0-9a-f-]{36}__[a-zA-Z0-9._-]*$ — 설계 §"Acceptance signals" 의 key 패턴 */
    private static final Pattern KEY_PATTERN =
            Pattern.compile("^(files|videos)/[0-9a-f-]{36}__[a-zA-Z0-9._-]*$");

    @Mock S3Client s3Client;
    @Mock S3Presigner s3Presigner;
    @Mock FileUploadProperties uploadProperties;

    @InjectMocks S3Service s3Service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s3Service, "bucketName", BUCKET);
    }

    // =================================================================
    // initiateUpload
    // =================================================================
    @Nested
    @DisplayName("initiateUpload(): CreateMultipartUploadRequest 구성")
    class InitiateUpload {

        @Test
        @DisplayName("bucket/key/contentType 가 정확히 SDK 호출에 전달된다")
        void buildsCreateRequestWithBucketKeyContentType() {
            // given
            given(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                    .willReturn(CreateMultipartUploadResponse.builder()
                            .bucket(BUCKET)
                            .uploadId("upload-id-1")
                            .build());

            S3ServiceDto.UploadInitiateRequest req = S3ServiceDto.UploadInitiateRequest.builder()
                    .originalFileName("contract.pdf")
                    .mimeType("application/pdf")
                    .fileSize(1024L)
                    .mediaType(MediaType.PDF)
                    .build();

            // when
            S3ServiceDto.UploadInitiateResponse res = s3Service.initiateUpload(req);

            // then
            ArgumentCaptor<CreateMultipartUploadRequest> captor =
                    ArgumentCaptor.forClass(CreateMultipartUploadRequest.class);
            then(s3Client).should(times(1)).createMultipartUpload(captor.capture());

            CreateMultipartUploadRequest sent = captor.getValue();
            assertThat(sent.bucket()).isEqualTo(BUCKET);
            assertThat(sent.contentType()).isEqualTo("application/pdf");
            assertThat(sent.key()).matches(KEY_PATTERN);
            // PDF 는 MediaType 분류상 "files/" subfolder 에 들어가야 한다
            assertThat(sent.key()).startsWith("files/");

            assertThat(res.getUploadId()).isEqualTo("upload-id-1");
            assertThat(res.getKey()).isEqualTo(sent.key());
            assertThat(res.getContentType()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("path traversal 시도(../etc/passwd) 는 sanitization 으로 무력화 + key 패턴 유지")
        void sanitizesFilename_neutralizesPathTraversal() {
            given(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                    .willReturn(CreateMultipartUploadResponse.builder()
                            .uploadId("u").build());

            S3ServiceDto.UploadInitiateRequest req = S3ServiceDto.UploadInitiateRequest.builder()
                    .originalFileName("../../../etc/passwd")
                    .mimeType("application/octet-stream")
                    .fileSize(1L)
                    .mediaType(MediaType.UNKNOWN)
                    .build();

            S3ServiceDto.UploadInitiateResponse res = s3Service.initiateUpload(req);

            // sanitization: '/' 와 첫 '..' 의 점들이 제거되어 'etcpasswd' 만 남거나
            // '..' 의 점이 남아도 슬래시는 제거되므로 traversal 은 절대 일어나지 않는다.
            assertThat(res.getKey()).matches(KEY_PATTERN);
            assertThat(res.getKey()).doesNotContain("/etc/");
            assertThat(res.getKey()).doesNotContain("../");
            // 정확히 한 번의 슬래시 (subfolder 와 UUID 사이)
            assertThat(res.getKey().chars().filter(c -> c == '/').count()).isEqualTo(1);
        }

        @Test
        @DisplayName("비디오 MIME → key subfolder 가 'videos/'")
        void videoMediaType_routesToVideosSubfolder() {
            given(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                    .willReturn(CreateMultipartUploadResponse.builder().uploadId("u").build());

            S3ServiceDto.UploadInitiateRequest req = S3ServiceDto.UploadInitiateRequest.builder()
                    .originalFileName("clip.mp4")
                    .mimeType("video/mp4")
                    .fileSize(MB)
                    .mediaType(MediaType.VIDEO)
                    .build();

            S3ServiceDto.UploadInitiateResponse res = s3Service.initiateUpload(req);

            assertThat(res.getKey()).startsWith("videos/");
        }

        @Test
        @DisplayName("이미지 MediaType → key subfolder 가 'files/'")
        void imageMediaType_routesToFilesSubfolder() {
            given(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                    .willReturn(CreateMultipartUploadResponse.builder().uploadId("u").build());

            S3ServiceDto.UploadInitiateRequest req = S3ServiceDto.UploadInitiateRequest.builder()
                    .originalFileName("photo.png")
                    .mimeType("image/png")
                    .fileSize(MB)
                    .mediaType(MediaType.IMAGE)
                    .build();

            S3ServiceDto.UploadInitiateResponse res = s3Service.initiateUpload(req);

            assertThat(res.getKey()).startsWith("files/");
        }

        @Test
        @DisplayName("mimeType 이 null 이면 파일명 확장자에서 contentType 추정")
        void guessesContentType_fromFilename_whenMimeMissing() {
            given(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                    .willReturn(CreateMultipartUploadResponse.builder().uploadId("u").build());

            S3ServiceDto.UploadInitiateRequest req = S3ServiceDto.UploadInitiateRequest.builder()
                    .originalFileName("document.pdf")
                    .mimeType(null)
                    .fileSize(1L)
                    .mediaType(MediaType.PDF)
                    .build();

            S3ServiceDto.UploadInitiateResponse res = s3Service.initiateUpload(req);

            // URLConnection.guessContentTypeFromName 가 .pdf 를 인식하므로
            // application/octet-stream 은 아닌 어떤 정상 MIME 이 와야 한다.
            assertThat(res.getContentType()).isNotBlank();
            // 최소한 octet-stream fallback 이 일어나지 않았는지 확인
            assertThat(res.getContentType().toLowerCase()).contains("pdf");
        }
    }

    // =================================================================
    // generatePresignedUrl — Content-Length / Content-MD5 결속
    // =================================================================
    @Nested
    @DisplayName("generatePresignedUrl(): Content-Length 항상 결속, Content-MD5 옵션")
    class GeneratePresignedUrl {

        @BeforeEach
        void setupPresigner() throws MalformedURLException {
            given(uploadProperties.getPresignedUrlTtlMinutes()).willReturn(TTL_MIN);
            // 스텁 객체는 외부에서 미리 만들어 둔다 — 외부 given(...).willReturn(...) 평가 중에
            // mock() / doReturn().when() 같은 Mockito 호출이 끼어들면 thread-local 스터빙
            // 컨텍스트가 꼬여 UnfinishedStubbingException 이 발생한다.
            PresignedUploadPartRequest stub = stubPresigned("https://s3.example.com/presigned");
            given(s3Presigner.presignUploadPart(any(UploadPartPresignRequest.class)))
                    .willReturn(stub);
        }

        @Test
        @DisplayName("contentLength > 0 이면 UploadPartRequest 에 항상 결속된다")
        void alwaysBindsContentLength_whenPositive() {
            S3ServiceDto.PresignedUrlRequest req = S3ServiceDto.PresignedUrlRequest.builder()
                    .key("files/abc/uuid__name.pdf")
                    .uploadId("upload-id")
                    .partNumber(1)
                    .contentLength(5L * MB)
                    .contentMd5Base64(null)
                    .build();

            URL url = s3Service.generatePresignedUrl(req);

            ArgumentCaptor<UploadPartPresignRequest> captor =
                    ArgumentCaptor.forClass(UploadPartPresignRequest.class);
            then(s3Presigner).should().presignUploadPart(captor.capture());

            UploadPartPresignRequest sent = captor.getValue();
            assertThat(sent.uploadPartRequest().contentLength()).isEqualTo(5L * MB);
            // MD5 는 미전달이므로 null
            assertThat(sent.uploadPartRequest().contentMD5()).isNull();
            assertThat(url.toString()).isEqualTo("https://s3.example.com/presigned");
        }

        @Test
        @DisplayName("contentMd5Base64 가 주어지면 UploadPartRequest 에 결속된다")
        void bindsMd5_whenProvided() {
            S3ServiceDto.PresignedUrlRequest req = S3ServiceDto.PresignedUrlRequest.builder()
                    .key("files/k")
                    .uploadId("u")
                    .partNumber(1)
                    .contentLength(1024L)
                    .contentMd5Base64("d41d8cd98f00b204e9800998ecf8427e")
                    .build();

            s3Service.generatePresignedUrl(req);

            ArgumentCaptor<UploadPartPresignRequest> captor =
                    ArgumentCaptor.forClass(UploadPartPresignRequest.class);
            then(s3Presigner).should().presignUploadPart(captor.capture());

            assertThat(captor.getValue().uploadPartRequest().contentMD5())
                    .isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
        }

        @Test
        @DisplayName("contentMd5Base64 가 null/blank 면 결속하지 않는다 (defense-in-depth 옵션)")
        void omitsMd5_whenAbsent() {
            S3ServiceDto.PresignedUrlRequest req = S3ServiceDto.PresignedUrlRequest.builder()
                    .key("k").uploadId("u").partNumber(1)
                    .contentLength(1024L)
                    .contentMd5Base64("   ")    // blank 도 미전달과 동일 처리
                    .build();

            s3Service.generatePresignedUrl(req);

            ArgumentCaptor<UploadPartPresignRequest> captor =
                    ArgumentCaptor.forClass(UploadPartPresignRequest.class);
            then(s3Presigner).should().presignUploadPart(captor.capture());

            assertThat(captor.getValue().uploadPartRequest().contentMD5()).isNull();
        }

        @Test
        @DisplayName("contentLength 가 null/0/음수 이면 SDK 에 결속하지 않는다 (방어적 가드)")
        void doesNotBindContentLength_whenNullOrNonPositive() {
            S3ServiceDto.PresignedUrlRequest req = S3ServiceDto.PresignedUrlRequest.builder()
                    .key("k").uploadId("u").partNumber(1)
                    .contentLength(null)
                    .build();

            s3Service.generatePresignedUrl(req);

            ArgumentCaptor<UploadPartPresignRequest> captor =
                    ArgumentCaptor.forClass(UploadPartPresignRequest.class);
            then(s3Presigner).should().presignUploadPart(captor.capture());

            assertThat(captor.getValue().uploadPartRequest().contentLength()).isNull();
        }

        @Test
        @DisplayName("signatureDuration 은 properties 에서 읽은 TTL 이며 file_security.md 권장 (≤ 15분) 을 지킨다")
        void signatureDuration_isFromProperties_andUnder15Minutes() {
            S3ServiceDto.PresignedUrlRequest req = S3ServiceDto.PresignedUrlRequest.builder()
                    .key("k").uploadId("u").partNumber(1)
                    .contentLength(1024L)
                    .build();

            s3Service.generatePresignedUrl(req);

            ArgumentCaptor<UploadPartPresignRequest> captor =
                    ArgumentCaptor.forClass(UploadPartPresignRequest.class);
            then(s3Presigner).should().presignUploadPart(captor.capture());

            Duration sigDuration = captor.getValue().signatureDuration();
            assertThat(sigDuration).isEqualTo(Duration.ofMinutes(TTL_MIN));
            // file_security.md §3 short TTL — 보안 가드레일
            assertThat(sigDuration).isLessThanOrEqualTo(Duration.ofMinutes(15));
        }

        @Test
        @DisplayName("UploadPartRequest 에 bucket/key/uploadId/partNumber 가 정확히 전달된다")
        void includesBucketKeyUploadIdPartNumber() {
            S3ServiceDto.PresignedUrlRequest req = S3ServiceDto.PresignedUrlRequest.builder()
                    .key("files/the-key")
                    .uploadId("upload-xyz")
                    .partNumber(7)
                    .contentLength(1L)
                    .build();

            s3Service.generatePresignedUrl(req);

            ArgumentCaptor<UploadPartPresignRequest> captor =
                    ArgumentCaptor.forClass(UploadPartPresignRequest.class);
            then(s3Presigner).should().presignUploadPart(captor.capture());

            var partReq = captor.getValue().uploadPartRequest();
            assertThat(partReq.bucket()).isEqualTo(BUCKET);
            assertThat(partReq.key()).isEqualTo("files/the-key");
            assertThat(partReq.uploadId()).isEqualTo("upload-xyz");
            assertThat(partReq.partNumber()).isEqualTo(7);
        }
    }

    // =================================================================
    // generatePartPresignedUrls — 일괄 발급
    // =================================================================
    @Nested
    @DisplayName("generatePartPresignedUrls(): partCount 만큼 일괄 발급")
    class GeneratePartPresignedUrls {

        @Test
        @DisplayName("100MB 입력 → presignUploadPart 가 정확히 2회 호출 + 각 응답 contentLength 합 = fileSize")
        void generates_partCountUrls_for100MBFile() throws MalformedURLException {
            given(uploadProperties.getPresignedUrlTtlMinutes()).willReturn(TTL_MIN);
            PresignedUploadPartRequest stub = stubPresigned("https://s3.example.com/p");
            given(s3Presigner.presignUploadPart(any(UploadPartPresignRequest.class)))
                    .willReturn(stub);

            long size = 100L * MB;
            S3ServiceDto.PartPresignedUrlRequest req = S3ServiceDto.PartPresignedUrlRequest.builder()
                    .key("files/k")
                    .uploadId("u")
                    .fileSize(size)
                    .build();

            List<S3ServiceDto.PartPresignedUrlResponse> result =
                    s3Service.generatePartPresignedUrls(req);

            // calculatePartCount(100MB) = ceil(100/64) = 2
            assertThat(result).hasSize(2);
            then(s3Presigner).should(times(2)).presignUploadPart(any(UploadPartPresignRequest.class));

            assertThat(result).extracting(S3ServiceDto.PartPresignedUrlResponse::getPartNumber)
                    .containsExactly(1, 2);
            // 모든 part 의 contentLength 합 = fileSize (마지막 part 가 잔여 흡수)
            long sum = result.stream().mapToLong(S3ServiceDto.PartPresignedUrlResponse::getContentLength).sum();
            assertThat(sum).isEqualTo(size);
        }

        @Test
        @DisplayName("50MB 입력 → 1개 URL 만 발급 (multipart 불필요한 작은 파일)")
        void generates_singleUrl_for50MBFile() throws MalformedURLException {
            given(uploadProperties.getPresignedUrlTtlMinutes()).willReturn(TTL_MIN);
            PresignedUploadPartRequest stub = stubPresigned("https://s3.example.com/p");
            given(s3Presigner.presignUploadPart(any(UploadPartPresignRequest.class)))
                    .willReturn(stub);

            S3ServiceDto.PartPresignedUrlRequest req = S3ServiceDto.PartPresignedUrlRequest.builder()
                    .key("k").uploadId("u").fileSize(50L * MB).build();

            List<S3ServiceDto.PartPresignedUrlResponse> result =
                    s3Service.generatePartPresignedUrls(req);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPartNumber()).isEqualTo(1);
            assertThat(result.get(0).getContentLength()).isEqualTo(50L * MB);
        }
    }

    // =================================================================
    // completeUpload — 정렬 검증
    // =================================================================
    @Nested
    @DisplayName("completeUpload(): partNumber 오름차순 정렬 강제")
    class CompleteUpload {

        @Test
        @DisplayName("입력이 역순/뒤섞여도 SDK 호출 시 partNumber 오름차순으로 정렬된다")
        void sortsPartsAscending_byPartNumber() {
            given(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                    .willReturn(CompleteMultipartUploadResponse.builder()
                            .location("https://s3.example.com/k")
                            .eTag("\"abc123\"")
                            .build());

            S3ServiceDto.CompleteUploadRequest req = S3ServiceDto.CompleteUploadRequest.builder()
                    .key("files/k")
                    .uploadId("u")
                    .parts(List.of(
                            // 일부러 뒤섞어서 입력
                            S3ServiceDto.CompleteUploadRequest.Part.builder().partNumber(3).etag("\"e3\"").build(),
                            S3ServiceDto.CompleteUploadRequest.Part.builder().partNumber(1).etag("\"e1\"").build(),
                            S3ServiceDto.CompleteUploadRequest.Part.builder().partNumber(2).etag("\"e2\"").build()
                    ))
                    .build();

            CompleteMultipartUploadResponse res = s3Service.completeUpload(req);

            ArgumentCaptor<CompleteMultipartUploadRequest> captor =
                    ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
            then(s3Client).should().completeMultipartUpload(captor.capture());

            List<CompletedPart> sentParts = captor.getValue().multipartUpload().parts();
            assertThat(sentParts).extracting(CompletedPart::partNumber)
                    .containsExactly(1, 2, 3);
            assertThat(sentParts).extracting(CompletedPart::eTag)
                    .containsExactly("\"e1\"", "\"e2\"", "\"e3\"");

            assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(captor.getValue().key()).isEqualTo("files/k");
            assertThat(captor.getValue().uploadId()).isEqualTo("u");

            assertThat(res.location()).isEqualTo("https://s3.example.com/k");
            assertThat(res.eTag()).isEqualTo("\"abc123\"");
        }
    }

    // =================================================================
    // abortUpload
    // =================================================================
    @Nested
    @DisplayName("abortUpload(): SDK 호출 + 인자 전달")
    class AbortUpload {

        @Test
        @DisplayName("AbortMultipartUploadRequest 가 정확히 1회 호출되며 bucket/key/uploadId 가 전달된다")
        void callsAbortOnce_withAllArguments() {
            S3ServiceDto.AbortUploadRequest req = S3ServiceDto.AbortUploadRequest.builder()
                    .key("files/k").uploadId("u").build();

            s3Service.abortUpload(req);

            ArgumentCaptor<AbortMultipartUploadRequest> captor =
                    ArgumentCaptor.forClass(AbortMultipartUploadRequest.class);
            then(s3Client).should(times(1)).abortMultipartUpload(captor.capture());

            assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(captor.getValue().key()).isEqualTo("files/k");
            assertThat(captor.getValue().uploadId()).isEqualTo("u");
        }
    }

    // =================================================================
    // listUploadedParts
    // =================================================================
    @Nested
    @DisplayName("listUploadedParts(): SDK 응답 → DTO 매핑")
    class ListUploadedParts {

        @Test
        @DisplayName("SDK Part 목록이 partNumber/etag/size 그대로 DTO 로 매핑된다")
        void mapsSdkPartsToDto() {
            var sdkResponse = software.amazon.awssdk.services.s3.model.ListPartsResponse.builder()
                    .parts(
                            Part.builder().partNumber(1).eTag("\"e1\"").size(5L * MB).build(),
                            Part.builder().partNumber(2).eTag("\"e2\"").size(3L * MB).build()
                    )
                    .build();
            given(s3Client.listParts(any(ListPartsRequest.class))).willReturn(sdkResponse);

            S3ServiceDto.ListPartsResponse res = s3Service.listUploadedParts("files/k", "u");

            ArgumentCaptor<ListPartsRequest> captor = ArgumentCaptor.forClass(ListPartsRequest.class);
            then(s3Client).should().listParts(captor.capture());
            assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(captor.getValue().key()).isEqualTo("files/k");
            assertThat(captor.getValue().uploadId()).isEqualTo("u");

            assertThat(res.getParts()).hasSize(2);
            assertThat(res.getParts()).extracting(S3ServiceDto.ListPartsResponse.UploadedPart::getPartNumber)
                    .containsExactly(1, 2);
            assertThat(res.getParts()).extracting(S3ServiceDto.ListPartsResponse.UploadedPart::getEtag)
                    .containsExactly("\"e1\"", "\"e2\"");
            assertThat(res.getParts()).extracting(S3ServiceDto.ListPartsResponse.UploadedPart::getSize)
                    .containsExactly(5L * MB, 3L * MB);
        }
    }

    // =================================================================
    // getObjectActualSize
    // =================================================================
    @Nested
    @DisplayName("getObjectActualSize(): HeadObject contentLength 반환")
    class GetObjectActualSize {

        @Test
        @DisplayName("HeadObject 응답의 contentLength 가 그대로 반환된다 (post-upload size 검증용)")
        void returnsHeadObjectContentLength() {
            given(s3Client.headObject(any(HeadObjectRequest.class)))
                    .willReturn(HeadObjectResponse.builder().contentLength(123_456_789L).build());

            long size = s3Service.getObjectActualSize("files/k");

            assertThat(size).isEqualTo(123_456_789L);

            ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
            then(s3Client).should().headObject(captor.capture());
            assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(captor.getValue().key()).isEqualTo("files/k");
        }
    }

    // =================================================================
    // calculatePartCount / calculatePartSize
    // =================================================================
    @Nested
    @DisplayName("calculatePartCount(): 경계값")
    class CalculatePartCount {

        @Test
        @DisplayName("50MB → 1 (RECOMMENDED_PART_SIZE_SMALL=64MB 미만 → multipart 불필요)")
        void fiftyMB_returnsOne() {
            assertThat(s3Service.calculatePartCount(50L * MB)).isEqualTo(1);
        }

        @Test
        @DisplayName("100MB → 2 (백로그 한도 = 100MB 정확히 = ceil(100/64) = 2)")
        void hundredMB_returnsTwo() {
            assertThat(s3Service.calculatePartCount(100L * MB)).isEqualTo(2);
        }

        @Test
        @DisplayName("64MB 정확히 → 1 (recommended part size 와 동일하면 한 덩어리)")
        void exactlySixtyFourMB_returnsOne() {
            assertThat(s3Service.calculatePartCount(64L * MB)).isEqualTo(1);
        }

        @Test
        @DisplayName("65MB → 2 (64MB 보다 1바이트라도 크면 두 덩어리)")
        void sixtyFiveMB_returnsTwo() {
            assertThat(s3Service.calculatePartCount(65L * MB)).isEqualTo(2);
        }

        @Test
        @DisplayName("null 입력 → 1 (방어적 가드)")
        void nullSize_returnsOne() {
            assertThat(s3Service.calculatePartCount(null)).isEqualTo(1);
        }

        @Test
        @DisplayName("0 → 1 (방어적 가드)")
        void zeroSize_returnsOne() {
            assertThat(s3Service.calculatePartCount(0L)).isEqualTo(1);
        }

        @Test
        @DisplayName("음수 → 1 (방어적 가드)")
        void negativeSize_returnsOne() {
            assertThat(s3Service.calculatePartCount(-1L)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("calculatePartSize(): 잔여 처리")
    class CalculatePartSize {

        @Test
        @DisplayName("totalParts=1 이면 전체 fileSize 가 그 part 의 크기")
        void singlePart_returnsFullFileSize() {
            assertThat(s3Service.calculatePartSize(7L * MB, 1, 1)).isEqualTo(7L * MB);
        }

        @Test
        @DisplayName("100MB / 2 parts → part1=50MB, part2=잔여(50MB)")
        void evenSplit_eachPartHalf() {
            long size = 100L * MB;
            long p1 = s3Service.calculatePartSize(size, 1, 2);
            long p2 = s3Service.calculatePartSize(size, 2, 2);
            assertThat(p1).isEqualTo(50L * MB);
            assertThat(p2).isEqualTo(50L * MB);
            assertThat(p1 + p2).isEqualTo(size);
        }

        @Test
        @DisplayName("나누어 떨어지지 않는 fileSize 는 마지막 part 가 잔여 흡수 (합 = fileSize)")
        void unevenSplit_lastPartAbsorbsRemainder() {
            long size = 100L * MB + 7;   // 잔여 7바이트
            long p1 = s3Service.calculatePartSize(size, 1, 2);
            long p2 = s3Service.calculatePartSize(size, 2, 2);
            assertThat(p1 + p2).isEqualTo(size);
            // 마지막 part 가 잔여를 흡수하므로 첫 part 보다 크거나 같다
            assertThat(p2).isGreaterThanOrEqualTo(p1);
        }
    }

    // =================================================================
    // No-mock-leak 보장: 호출되지 않은 mock 검증 (음성 회귀 방어)
    // =================================================================
    @Nested
    @DisplayName("음성 회귀 방어")
    class NoUnexpectedCalls {

        @Test
        @DisplayName("calculatePartCount 는 SDK 를 호출하지 않는다 (순수 계산)")
        void calculatePartCount_doesNotCallSdk() {
            s3Service.calculatePartCount(100L * MB);

            then(s3Client).should(never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
            then(s3Presigner).should(never()).presignUploadPart(any(UploadPartPresignRequest.class));
        }

        @Test
        @DisplayName("calculatePartSize 는 SDK 를 호출하지 않는다 (순수 계산)")
        void calculatePartSize_doesNotCallSdk() {
            s3Service.calculatePartSize(100L * MB, 1, 2);

            then(s3Client).shouldHaveNoInteractions();
            then(s3Presigner).shouldHaveNoInteractions();
        }
    }

    // =================================================================
    // 잘못된 입력 방어 검증
    // =================================================================
    @Nested
    @DisplayName("입력 방어")
    class DefensiveInput {

        @Test
        @DisplayName("null 파일명 입력에도 initiateUpload 가 동작하며 key 의 sanitized 영역이 'unknown' 으로 대체된다")
        void initiate_withNullFilename_replacesWithUnknownToken() {
            given(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                    .willReturn(CreateMultipartUploadResponse.builder().uploadId("u").build());

            S3ServiceDto.UploadInitiateRequest req = S3ServiceDto.UploadInitiateRequest.builder()
                    .originalFileName(null)
                    .mimeType("application/octet-stream")
                    .fileSize(1L)
                    .mediaType(MediaType.UNKNOWN)
                    .build();

            assertThat(s3Service.initiateUpload(req).getKey()).endsWith("__unknown");
        }

        @Test
        @DisplayName("sanitization 결과가 빈 문자열이 되는 입력도 'unknown' fallback")
        void initiate_withAllStrippedFilename_fallsBackToUnknown() {
            given(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                    .willReturn(CreateMultipartUploadResponse.builder().uploadId("u").build());

            S3ServiceDto.UploadInitiateRequest req = S3ServiceDto.UploadInitiateRequest.builder()
                    // 영문/숫자/.-_ 외 모든 문자 → sanitization 후 빈 문자열
                    .originalFileName("///\\\\##??!!")
                    .mimeType("application/octet-stream")
                    .fileSize(1L)
                    .mediaType(MediaType.UNKNOWN)
                    .build();

            assertThat(s3Service.initiateUpload(req).getKey()).endsWith("__unknown");
        }
    }

    // =================================================================
    // Helpers
    // =================================================================

    /**
     * SUT 가 응답 객체에서 {@code .url()} 만 호출하므로 Mockito mock 으로 충분하다 — SDK 의
     * {@code PresignedUploadPartRequest} 빌더는 internal 메서드들이 많아 직접 생성이 까다롭다.
     *
     * <p><b>주의 — {@code given(mock.url()).willReturn(url)} 대신 {@code doReturn().when()}
     * 사용 이유</b>: 호출자가 {@code given(s3Presigner.presignUploadPart(...)).willReturn(stubPresigned(...))}
     * 형태로 쓰는데, 외부 {@code given(...)} 평가 도중 인자로 넘어가는 {@code stubPresigned(...)}
     * 안에서 또 다른 {@code given(mock.url()).willReturn(url)} 을 호출하면, Mockito 의 thread-local
     * 스터빙 컨텍스트가 꼬여 {@code UnfinishedStubbingException} 이 발생한다 ("stubbing the
     * behaviour of another mock inside before 'thenReturn' instruction is completed"). {@code
     * doReturn(...).when(mock).method()} 은 {@code when()} 호출 시점에 즉시 스텁이 등록되어
     * 외부 {@code willReturn(...)} 평가 시점과 충돌하지 않는다.
     */
    private static PresignedUploadPartRequest stubPresigned(String urlString) throws MalformedURLException {
        URL url = URI.create(urlString).toURL();
        PresignedUploadPartRequest mockPresigned = mock(PresignedUploadPartRequest.class);
        org.mockito.Mockito.doReturn(url).when(mockPresigned).url();
        return mockPresigned;
    }
}
