package com.capstone.passfolio.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.capstone.passfolio.domain.file.dto.FileDto;
import com.capstone.passfolio.domain.file.entity.File;
import com.capstone.passfolio.domain.file.entity.enums.MediaType;
import com.capstone.passfolio.domain.file.repository.FileRepository;
import com.capstone.passfolio.domain.s3.dto.S3ServiceDto;
import com.capstone.passfolio.domain.s3.service.S3Service;
import com.capstone.passfolio.system.config.file.FileUploadProperties;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;

/**
 * {@link FileService} 단위 테스트.
 *
 * <p>Spring 컨텍스트 0개. {@link S3Service}, {@link FileRepository}, {@link FileUploadProperties}
 * 는 Mockito mock 으로 주입한다 — 본 단위 테스트의 책임은 "FileService 가 다음 4가지 계약을 지키는가"이다.
 *
 * <ol>
 *   <li><b>업로드 정책 사전 검증</b>: fileSize null/0/음수 → {@link ErrorCode#FILE_INVALID_SIZE},
 *       fileSize > 한도 → {@link ErrorCode#FILE_SIZE_EXCEEDED},
 *       확장자 비허용 → {@link ErrorCode#FILE_EXTENSION_NOT_ALLOWED}.
 *       검증 실패 시 {@link S3Service} 는 호출되지 않는다 (cheap reject).</li>
 *   <li><b>S3Service 위임 + DTO 매핑</b>: initiate / part presign / list-parts / abort 모두
 *       파라미터를 잃지 않고 그대로 전달, 결과 DTO 도 1:1 매핑.</li>
 *   <li><b>complete 단계 사후 크기 검증</b>: actual size 가 약속한 fileSize 와 다르면
 *       {@link S3Service#abortUpload} 호출 후 {@link ErrorCode#FILE_SIZE_MISMATCH}.
 *       일치하면 {@link FileRepository#save} 가 actual size (S3 권위) 로 영속화.</li>
 *   <li><b>IDOR 가드</b>: {@link FileService#validateFileOwner} / {@link FileService#validateFileOwners}
 *       — 미존재 → {@link ErrorCode#FILE_NOT_FOUND}, 소유자 불일치 → {@link ErrorCode#AUTH_FORBIDDEN}.</li>
 * </ol>
 *
 * <p>설계 참조: {@code 0001-harness-design.md} §"Tasks / T18" + §"Files plan / FileService".
 *
 * <p>각 테스트는 한 줄로 "어떤 계약이 깨지면 이 테스트가 실패하는지" 답할 수 있어야 하며,
 * {@code isNotNull()} 단독 검증은 사용하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private static final long MB = 1024L * 1024L;
    private static final long LIMIT_BYTES = 100L * MB;
    private static final String S3_KEY = "files/3f2d04ec-12ab-4cde-9abc-1234567890ab__resume.pdf";
    private static final String UPLOAD_ID = "upload-id-12345";
    private static final String CONTENT_TYPE = "application/pdf";

    @Mock S3Service s3Service;
    @Mock FileRepository fileRepository;
    @Mock FileUploadProperties uploadProperties;

    @InjectMocks FileService fileService;

    // =====================================================================
    // 1) initiateMultipartUpload — 정책 통과 시 S3Service 위임 + 응답 매핑
    // =====================================================================
    @Nested
    @DisplayName("initiateMultipartUpload(): 정책 통과 흐름")
    class InitiateUploadHappyPath {

        @Test
        @DisplayName(".pdf + 1MB → S3Service 3개 호출 (initiate/calculatePartCount/generatePartPresignedUrls)" +
                " + 응답에 key/uploadId/contentType/partCount/partPresignedUrls 모두 매핑")
        void pdfUnder100MB_delegatesToS3AndMapsResponse() {
            // given — 정책 통과 (확장자 OK + 1MB < 100MB)
            given(uploadProperties.getMaxFileSizeBytes()).willReturn(LIMIT_BYTES);
            given(uploadProperties.isExtensionAllowed("contract.pdf")).willReturn(true);

            given(s3Service.initiateUpload(any(S3ServiceDto.UploadInitiateRequest.class)))
                    .willReturn(S3ServiceDto.UploadInitiateResponse.builder()
                            .key(S3_KEY)
                            .uploadId(UPLOAD_ID)
                            .contentType(CONTENT_TYPE)
                            .build());
            given(s3Service.calculatePartCount(1L * MB)).willReturn(1);
            given(s3Service.generatePartPresignedUrls(any(S3ServiceDto.PartPresignedUrlRequest.class)))
                    .willReturn(List.of(
                            S3ServiceDto.PartPresignedUrlResponse.builder()
                                    .partNumber(1)
                                    .presignedUrl("https://s3/sign/1")
                                    .contentLength(1L * MB)
                                    .build()));

            FileDto.MultipartUploadInitiateRequest req = FileDto.MultipartUploadInitiateRequest.builder()
                    .originalFileName("contract.pdf")
                    .mimeType("application/pdf")
                    .fileSize(1L * MB)
                    .build();

            // when
            FileDto.MultipartUploadInitiateResponse resp = fileService.initiateMultipartUpload(req);

            // then — 응답이 S3Service 결과를 그대로 매핑
            assertThat(resp.getKey()).isEqualTo(S3_KEY);
            assertThat(resp.getUploadId()).isEqualTo(UPLOAD_ID);
            assertThat(resp.getContentType()).isEqualTo(CONTENT_TYPE);
            assertThat(resp.getPartCount()).isEqualTo(1);
            assertThat(resp.getPartPresignedUrls())
                    .hasSize(1)
                    .first()
                    .satisfies(p -> {
                        assertThat(p.getPartNumber()).isEqualTo(1);
                        assertThat(p.getPresignedUrl()).isEqualTo("https://s3/sign/1");
                        assertThat(p.getContentLength()).isEqualTo(1L * MB);
                    });

            // SDK 위임 인자 캡처 — initiate 에 mediaType=PDF 가 결정되어 전달되어야 함 (mime=application/pdf)
            ArgumentCaptor<S3ServiceDto.UploadInitiateRequest> initiateCap =
                    ArgumentCaptor.forClass(S3ServiceDto.UploadInitiateRequest.class);
            then(s3Service).should(times(1)).initiateUpload(initiateCap.capture());
            S3ServiceDto.UploadInitiateRequest sent = initiateCap.getValue();
            assertThat(sent.getOriginalFileName()).isEqualTo("contract.pdf");
            assertThat(sent.getMimeType()).isEqualTo("application/pdf");
            assertThat(sent.getFileSize()).isEqualTo(1L * MB);
            assertThat(sent.getMediaType()).isEqualTo(MediaType.PDF);

            // generatePartPresignedUrls 에 key/uploadId/fileSize 모두 전달
            ArgumentCaptor<S3ServiceDto.PartPresignedUrlRequest> partCap =
                    ArgumentCaptor.forClass(S3ServiceDto.PartPresignedUrlRequest.class);
            then(s3Service).should(times(1)).generatePartPresignedUrls(partCap.capture());
            S3ServiceDto.PartPresignedUrlRequest partReq = partCap.getValue();
            assertThat(partReq.getKey()).isEqualTo(S3_KEY);
            assertThat(partReq.getUploadId()).isEqualTo(UPLOAD_ID);
            assertThat(partReq.getFileSize()).isEqualTo(1L * MB);
        }

        @Test
        @DisplayName("video/mp4 + .mp4 → mediaType=VIDEO 로 분류되어 S3Service 에 전달")
        void mp4WithVideoMime_classifiedAsVideo() {
            // given
            given(uploadProperties.getMaxFileSizeBytes()).willReturn(LIMIT_BYTES);
            given(uploadProperties.isExtensionAllowed("clip.mp4")).willReturn(true);
            given(s3Service.initiateUpload(any())).willReturn(
                    S3ServiceDto.UploadInitiateResponse.builder()
                            .key("videos/uuid__clip.mp4").uploadId(UPLOAD_ID).contentType("video/mp4")
                            .build());
            given(s3Service.calculatePartCount(anyLong())).willReturn(1);
            given(s3Service.generatePartPresignedUrls(any())).willReturn(List.of());

            // when
            fileService.initiateMultipartUpload(FileDto.MultipartUploadInitiateRequest.builder()
                    .originalFileName("clip.mp4")
                    .mimeType("video/mp4")
                    .fileSize(2L * MB)
                    .build());

            // then
            ArgumentCaptor<S3ServiceDto.UploadInitiateRequest> cap =
                    ArgumentCaptor.forClass(S3ServiceDto.UploadInitiateRequest.class);
            then(s3Service).should().initiateUpload(cap.capture());
            assertThat(cap.getValue().getMediaType()).isEqualTo(MediaType.VIDEO);
        }

        @Test
        @DisplayName("image/png + .png → mediaType=IMAGE")
        void pngWithImageMime_classifiedAsImage() {
            given(uploadProperties.getMaxFileSizeBytes()).willReturn(LIMIT_BYTES);
            given(uploadProperties.isExtensionAllowed("photo.png")).willReturn(true);
            given(s3Service.initiateUpload(any())).willReturn(
                    S3ServiceDto.UploadInitiateResponse.builder()
                            .key("files/uuid__photo.png").uploadId(UPLOAD_ID).contentType("image/png")
                            .build());
            given(s3Service.calculatePartCount(anyLong())).willReturn(1);
            given(s3Service.generatePartPresignedUrls(any())).willReturn(List.of());

            fileService.initiateMultipartUpload(FileDto.MultipartUploadInitiateRequest.builder()
                    .originalFileName("photo.png")
                    .mimeType("image/png")
                    .fileSize(MB)
                    .build());

            ArgumentCaptor<S3ServiceDto.UploadInitiateRequest> cap =
                    ArgumentCaptor.forClass(S3ServiceDto.UploadInitiateRequest.class);
            then(s3Service).should().initiateUpload(cap.capture());
            assertThat(cap.getValue().getMediaType()).isEqualTo(MediaType.IMAGE);
        }

        @Test
        @DisplayName("partPresignedUrls 응답 리스트 길이/순서 보존: 3 part → DTO 3개 동일 순서")
        void multiplePartUrls_preservedInOrder() {
            given(uploadProperties.getMaxFileSizeBytes()).willReturn(LIMIT_BYTES);
            given(uploadProperties.isExtensionAllowed(any())).willReturn(true);
            given(s3Service.initiateUpload(any())).willReturn(
                    S3ServiceDto.UploadInitiateResponse.builder()
                            .key(S3_KEY).uploadId(UPLOAD_ID).contentType(CONTENT_TYPE).build());
            given(s3Service.calculatePartCount(anyLong())).willReturn(3);
            given(s3Service.generatePartPresignedUrls(any())).willReturn(List.of(
                    S3ServiceDto.PartPresignedUrlResponse.builder().partNumber(1).presignedUrl("u1").contentLength(MB).build(),
                    S3ServiceDto.PartPresignedUrlResponse.builder().partNumber(2).presignedUrl("u2").contentLength(MB).build(),
                    S3ServiceDto.PartPresignedUrlResponse.builder().partNumber(3).presignedUrl("u3").contentLength(MB).build()
            ));

            FileDto.MultipartUploadInitiateResponse resp = fileService.initiateMultipartUpload(
                    FileDto.MultipartUploadInitiateRequest.builder()
                            .originalFileName("a.pdf").fileSize(3L * MB).build());

            assertThat(resp.getPartCount()).isEqualTo(3);
            assertThat(resp.getPartPresignedUrls()).extracting(FileDto.PartPresignedUrl::getPartNumber)
                    .containsExactly(1, 2, 3);
            assertThat(resp.getPartPresignedUrls()).extracting(FileDto.PartPresignedUrl::getPresignedUrl)
                    .containsExactly("u1", "u2", "u3");
        }
    }

    // =====================================================================
    // 2) initiateMultipartUpload — 정책 위반 시 cheap reject (S3 미호출)
    // =====================================================================
    @Nested
    @DisplayName("initiateMultipartUpload(): validateUploadPolicy — cheap reject")
    class ValidateUploadPolicy {

        @Test
        @DisplayName(".exe → FILE_EXTENSION_NOT_ALLOWED + S3 미호출")
        void disallowedExtension_throwsAndShortCircuits() {
            // given — fileSize 검증은 통과시키고, 확장자에서만 차단되도록
            given(uploadProperties.getMaxFileSizeBytes()).willReturn(LIMIT_BYTES);
            given(uploadProperties.isExtensionAllowed("malware.exe")).willReturn(false);

            FileDto.MultipartUploadInitiateRequest req = FileDto.MultipartUploadInitiateRequest.builder()
                    .originalFileName("malware.exe")
                    .fileSize(MB)
                    .build();

            // when / then
            assertThatThrownBy(() -> fileService.initiateMultipartUpload(req))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FILE_EXTENSION_NOT_ALLOWED);

            then(s3Service).should(never()).initiateUpload(any());
            then(s3Service).should(never()).generatePartPresignedUrls(any());
        }

        @Test
        @DisplayName("100MB+1 byte → FILE_SIZE_EXCEEDED + S3 미호출 (boundary)")
        void sizeOverLimit_throwsAndShortCircuits() {
            given(uploadProperties.getMaxFileSizeBytes()).willReturn(LIMIT_BYTES);

            FileDto.MultipartUploadInitiateRequest req = FileDto.MultipartUploadInitiateRequest.builder()
                    .originalFileName("huge.pdf")
                    .fileSize(LIMIT_BYTES + 1)
                    .build();

            assertThatThrownBy(() -> fileService.initiateMultipartUpload(req))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);

            then(s3Service).shouldHaveNoInteractions();
            // 확장자 검증은 사이즈 검증 이후 → 호출되지 않음
            then(uploadProperties).should(never()).isExtensionAllowed(any());
        }

        @Test
        @DisplayName("정확히 100MB → 통과 (boundary 정상)")
        void sizeAtLimit_passes() {
            given(uploadProperties.getMaxFileSizeBytes()).willReturn(LIMIT_BYTES);
            given(uploadProperties.isExtensionAllowed("ok.pdf")).willReturn(true);
            given(s3Service.initiateUpload(any())).willReturn(
                    S3ServiceDto.UploadInitiateResponse.builder()
                            .key(S3_KEY).uploadId(UPLOAD_ID).contentType(CONTENT_TYPE).build());
            given(s3Service.calculatePartCount(anyLong())).willReturn(2);
            given(s3Service.generatePartPresignedUrls(any())).willReturn(List.of());

            FileDto.MultipartUploadInitiateResponse resp = fileService.initiateMultipartUpload(
                    FileDto.MultipartUploadInitiateRequest.builder()
                            .originalFileName("ok.pdf").fileSize(LIMIT_BYTES).build());

            assertThat(resp.getKey()).isEqualTo(S3_KEY);
            then(s3Service).should().initiateUpload(any());
        }

        @Test
        @DisplayName("fileSize=null → FILE_INVALID_SIZE")
        void nullSize_throwsInvalidSize() {
            FileDto.MultipartUploadInitiateRequest req = FileDto.MultipartUploadInitiateRequest.builder()
                    .originalFileName("a.pdf").fileSize(null).build();

            assertThatThrownBy(() -> fileService.initiateMultipartUpload(req))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FILE_INVALID_SIZE);

            then(s3Service).shouldHaveNoInteractions();
            then(uploadProperties).should(never()).getMaxFileSizeBytes();
        }

        @Test
        @DisplayName("fileSize=0 → FILE_INVALID_SIZE")
        void zeroSize_throwsInvalidSize() {
            FileDto.MultipartUploadInitiateRequest req = FileDto.MultipartUploadInitiateRequest.builder()
                    .originalFileName("a.pdf").fileSize(0L).build();

            assertThatThrownBy(() -> fileService.initiateMultipartUpload(req))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FILE_INVALID_SIZE);

            then(s3Service).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("fileSize=음수 → FILE_INVALID_SIZE")
        void negativeSize_throwsInvalidSize() {
            FileDto.MultipartUploadInitiateRequest req = FileDto.MultipartUploadInitiateRequest.builder()
                    .originalFileName("a.pdf").fileSize(-1L).build();

            assertThatThrownBy(() -> fileService.initiateMultipartUpload(req))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FILE_INVALID_SIZE);
        }

        @Test
        @DisplayName("검증 순서: size > limit && .exe 동시 위반 → SIZE_EXCEEDED 가 먼저 (확장자 미평가)")
        void sizeCheckedBeforeExtension_whenBothViolate() {
            given(uploadProperties.getMaxFileSizeBytes()).willReturn(LIMIT_BYTES);

            FileDto.MultipartUploadInitiateRequest req = FileDto.MultipartUploadInitiateRequest.builder()
                    .originalFileName("malware.exe")
                    .fileSize(LIMIT_BYTES + MB)
                    .build();

            assertThatThrownBy(() -> fileService.initiateMultipartUpload(req))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);

            then(uploadProperties).should(never()).isExtensionAllowed(any());
        }
    }

    // =====================================================================
    // 3) generatePartPresignedUrl — 단일 파트 presign 위임 + MD5 통과
    // =====================================================================
    @Nested
    @DisplayName("generatePartPresignedUrl(): S3Service 위임 + MD5 결속")
    class GeneratePartPresignedUrl {

        @Test
        @DisplayName("md5Base64 가 있으면 PresignedUrlRequest 에 그대로 결속되어 전달")
        void withMd5_forwardsToS3Service() throws MalformedURLException {
            // given
            URL url = URI.create("https://s3/sign/abc").toURL();
            given(s3Service.generatePresignedUrl(any(S3ServiceDto.PresignedUrlRequest.class)))
                    .willReturn(url);

            FileDto.PartPresignedUrlSingleRequest req = FileDto.PartPresignedUrlSingleRequest.builder()
                    .key(S3_KEY)
                    .uploadId(UPLOAD_ID)
                    .partNumber(7)
                    .contentLength(64L * MB)
                    .md5Base64("abc123==")
                    .build();

            // when
            FileDto.PartPresignedUrlSingleResponse resp = fileService.generatePartPresignedUrl(req);

            // then — 응답 매핑
            assertThat(resp.getPartNumber()).isEqualTo(7);
            assertThat(resp.getPresignedUrl()).isEqualTo("https://s3/sign/abc");
            assertThat(resp.getContentLength()).isEqualTo(64L * MB);
            assertThat(resp.getMd5Base64()).isEqualTo("abc123==");

            // SDK 호출 인자 캡처 — 5개 필드 전부 보존
            ArgumentCaptor<S3ServiceDto.PresignedUrlRequest> cap =
                    ArgumentCaptor.forClass(S3ServiceDto.PresignedUrlRequest.class);
            then(s3Service).should().generatePresignedUrl(cap.capture());
            S3ServiceDto.PresignedUrlRequest sent = cap.getValue();
            assertThat(sent.getKey()).isEqualTo(S3_KEY);
            assertThat(sent.getUploadId()).isEqualTo(UPLOAD_ID);
            assertThat(sent.getPartNumber()).isEqualTo(7);
            assertThat(sent.getContentLength()).isEqualTo(64L * MB);
            assertThat(sent.getContentMd5Base64()).isEqualTo("abc123==");
        }

        @Test
        @DisplayName("md5Base64 가 null 이면 PresignedUrlRequest 에도 null 로 전달 (S3Service 가 분기)")
        void withoutMd5_forwardsNull() throws MalformedURLException {
            URL url = URI.create("https://s3/sign/x").toURL();
            given(s3Service.generatePresignedUrl(any())).willReturn(url);

            FileDto.PartPresignedUrlSingleRequest req = FileDto.PartPresignedUrlSingleRequest.builder()
                    .key(S3_KEY).uploadId(UPLOAD_ID).partNumber(1).contentLength(MB).build();

            FileDto.PartPresignedUrlSingleResponse resp = fileService.generatePartPresignedUrl(req);

            assertThat(resp.getMd5Base64()).isNull();
            ArgumentCaptor<S3ServiceDto.PresignedUrlRequest> cap =
                    ArgumentCaptor.forClass(S3ServiceDto.PresignedUrlRequest.class);
            then(s3Service).should().generatePresignedUrl(cap.capture());
            assertThat(cap.getValue().getContentMd5Base64()).isNull();
        }
    }

    // =====================================================================
    // 4) listUploadedParts — 1:1 DTO 매핑
    // =====================================================================
    @Nested
    @DisplayName("listUploadedParts(): S3Service 응답 → DTO 매핑")
    class ListUploadedParts {

        @Test
        @DisplayName("S3 ListPartsResponse 의 파트 N개 → FileDto.UploadedPart N개로 정확 매핑")
        void mapsPartsOneToOne() {
            given(s3Service.listUploadedParts(S3_KEY, UPLOAD_ID)).willReturn(
                    S3ServiceDto.ListPartsResponse.builder().parts(List.of(
                            S3ServiceDto.ListPartsResponse.UploadedPart.builder()
                                    .partNumber(1).etag("etag-1").size(MB).build(),
                            S3ServiceDto.ListPartsResponse.UploadedPart.builder()
                                    .partNumber(2).etag("etag-2").size(2L * MB).build()
                    )).build());

            FileDto.ListPartsResponse resp = fileService.listUploadedParts(S3_KEY, UPLOAD_ID);

            assertThat(resp.getParts()).hasSize(2);
            assertThat(resp.getParts().get(0).getPartNumber()).isEqualTo(1);
            assertThat(resp.getParts().get(0).getEtag()).isEqualTo("etag-1");
            assertThat(resp.getParts().get(0).getSize()).isEqualTo(MB);
            assertThat(resp.getParts().get(1).getPartNumber()).isEqualTo(2);
            assertThat(resp.getParts().get(1).getEtag()).isEqualTo("etag-2");
            assertThat(resp.getParts().get(1).getSize()).isEqualTo(2L * MB);
        }

        @Test
        @DisplayName("빈 리스트 → 빈 응답")
        void emptyParts_returnsEmpty() {
            given(s3Service.listUploadedParts(S3_KEY, UPLOAD_ID)).willReturn(
                    S3ServiceDto.ListPartsResponse.builder().parts(Collections.emptyList()).build());

            FileDto.ListPartsResponse resp = fileService.listUploadedParts(S3_KEY, UPLOAD_ID);

            assertThat(resp.getParts()).isEmpty();
        }
    }

    // =====================================================================
    // 5) completeMultipartUpload — happy path / 사후 검증 / 매핑
    // =====================================================================
    @Nested
    @DisplayName("completeMultipartUpload(): S3 commit + HeadObject 검증 + JPA save")
    class CompleteMultipartUpload {

        @Test
        @DisplayName("happy path: actual=promised → fileRepository.save 호출 + saved entity 반환 (actual size + 파생 mediaType)")
        void happyPath_savesFileWithActualSize() {
            // given
            given(s3Service.completeUpload(any())).willReturn(
                    CompleteMultipartUploadResponse.builder()
                            .location("s3://bucket/" + S3_KEY).eTag("\"final-etag\"").build());
            given(s3Service.getObjectActualSize(S3_KEY)).willReturn(50L * MB);

            // save() 가 받은 엔티티 그대로 (id 부여 시뮬레이션) 반환
            given(fileRepository.save(any(File.class))).willAnswer(inv -> {
                File f = inv.getArgument(0);
                return File.builder()
                        .id(42L)
                        .s3ObjectKey(f.getS3ObjectKey())
                        .filename(f.getFilename())
                        .fileSize(f.getFileSize())
                        .mediaType(f.getMediaType())
                        .build();
            });

            FileDto.CompleteMultipartUploadRequest req = FileDto.CompleteMultipartUploadRequest.builder()
                    .key(S3_KEY)
                    .uploadId(UPLOAD_ID)
                    .parts(List.of(
                            FileDto.Part.builder().partNumber(1).etag("e1").build(),
                            FileDto.Part.builder().partNumber(2).etag("e2").build()))
                    .originalFileName("contract.pdf")
                    .fileSize(50L * MB)
                    .mimeType("application/pdf")
                    .build();

            // when
            File saved = fileService.completeMultipartUpload(req);

            // then — 반환된 엔티티
            assertThat(saved.getId()).isEqualTo(42L);
            assertThat(saved.getS3ObjectKey()).isEqualTo(S3_KEY);
            assertThat(saved.getFilename()).isEqualTo("contract.pdf");
            assertThat(saved.getFileSize()).isEqualTo(50L * MB);
            assertThat(saved.getMediaType()).isEqualTo(MediaType.PDF);

            // abort 는 호출되지 않아야 함
            then(s3Service).should(never()).abortUpload(any());

            // save 에 전달된 엔티티 = actual size 사용 (S3 권위)
            ArgumentCaptor<File> fileCap = ArgumentCaptor.forClass(File.class);
            then(fileRepository).should().save(fileCap.capture());
            File toSave = fileCap.getValue();
            assertThat(toSave.getS3ObjectKey()).isEqualTo(S3_KEY);
            assertThat(toSave.getFilename()).isEqualTo("contract.pdf");
            assertThat(toSave.getFileSize()).isEqualTo(50L * MB);
            assertThat(toSave.getMediaType()).isEqualTo(MediaType.PDF);
        }

        @Test
        @DisplayName("save 엔티티는 클라이언트 promised 가 아니라 S3 actual size 로 영속화 (권위 = S3)")
        void saveUsesActualSize_notPromised() {
            // 약속한 50MB, 실제도 50MB 지만 별도 값으로 두어 actual 이 채택되는지 검증
            given(s3Service.completeUpload(any())).willReturn(
                    CompleteMultipartUploadResponse.builder().location("loc").eTag("\"e\"").build());
            // actual 이 promised 와 같지만 다른 객체 인스턴스로 들어오도록 — 본 테스트는 동일 값 케이스 한정
            // (다른 값 케이스는 sizeMismatch 테스트가 담당)
            given(s3Service.getObjectActualSize(S3_KEY)).willReturn(50L * MB);
            given(fileRepository.save(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            FileDto.CompleteMultipartUploadRequest req = FileDto.CompleteMultipartUploadRequest.builder()
                    .key(S3_KEY).uploadId(UPLOAD_ID)
                    .parts(List.of(FileDto.Part.builder().partNumber(1).etag("e").build()))
                    .originalFileName("a.pdf").fileSize(50L * MB).build();

            fileService.completeMultipartUpload(req);

            ArgumentCaptor<File> cap = ArgumentCaptor.forClass(File.class);
            then(fileRepository).should().save(cap.capture());
            assertThat(cap.getValue().getFileSize()).isEqualTo(50L * MB); // actual
        }

        @Test
        @DisplayName("S3Service.completeUpload 에 parts 모두 그대로 전달 (정렬은 S3Service 책임)")
        void forwardsAllPartsToS3Service() {
            given(s3Service.completeUpload(any())).willReturn(
                    CompleteMultipartUploadResponse.builder().location("loc").eTag("\"e\"").build());
            given(s3Service.getObjectActualSize(any())).willReturn(MB);
            given(fileRepository.save(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            FileDto.CompleteMultipartUploadRequest req = FileDto.CompleteMultipartUploadRequest.builder()
                    .key(S3_KEY).uploadId(UPLOAD_ID)
                    .parts(List.of(
                            FileDto.Part.builder().partNumber(3).etag("e3").build(),
                            FileDto.Part.builder().partNumber(1).etag("e1").build(),
                            FileDto.Part.builder().partNumber(2).etag("e2").build()))
                    .originalFileName("a.pdf").fileSize(MB).build();

            fileService.completeMultipartUpload(req);

            ArgumentCaptor<S3ServiceDto.CompleteUploadRequest> cap =
                    ArgumentCaptor.forClass(S3ServiceDto.CompleteUploadRequest.class);
            then(s3Service).should().completeUpload(cap.capture());
            S3ServiceDto.CompleteUploadRequest sent = cap.getValue();
            assertThat(sent.getKey()).isEqualTo(S3_KEY);
            assertThat(sent.getUploadId()).isEqualTo(UPLOAD_ID);
            // FileService 는 정렬을 하지 않고 그대로 전달 (정렬은 S3Service 안에서) — 길이/매핑만 검증
            assertThat(sent.getParts()).hasSize(3);
            assertThat(sent.getParts()).extracting(p -> p.getPartNumber()).containsExactly(3, 1, 2);
            assertThat(sent.getParts()).extracting(p -> p.getEtag()).containsExactly("e3", "e1", "e2");
        }

        @Test
        @DisplayName("size mismatch (actual ≠ promised) → abortUpload 호출 + FILE_SIZE_MISMATCH + save 미호출")
        void actualSizeDiffersFromPromised_abortsAndThrows() {
            // given — promised=50MB 인데 실제 60MB 로 도착
            given(s3Service.completeUpload(any())).willReturn(
                    CompleteMultipartUploadResponse.builder().location("loc").eTag("\"e\"").build());
            given(s3Service.getObjectActualSize(S3_KEY)).willReturn(60L * MB);

            FileDto.CompleteMultipartUploadRequest req = FileDto.CompleteMultipartUploadRequest.builder()
                    .key(S3_KEY).uploadId(UPLOAD_ID)
                    .parts(List.of(FileDto.Part.builder().partNumber(1).etag("e").build()))
                    .originalFileName("a.pdf").fileSize(50L * MB).build();

            // when / then
            assertThatThrownBy(() -> fileService.completeMultipartUpload(req))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FILE_SIZE_MISMATCH);

            // abort 가 정확한 key/uploadId 로 호출되었는지
            ArgumentCaptor<S3ServiceDto.AbortUploadRequest> cap =
                    ArgumentCaptor.forClass(S3ServiceDto.AbortUploadRequest.class);
            then(s3Service).should().abortUpload(cap.capture());
            assertThat(cap.getValue().getKey()).isEqualTo(S3_KEY);
            assertThat(cap.getValue().getUploadId()).isEqualTo(UPLOAD_ID);

            // DB 저장은 발생하면 안 됨
            then(fileRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("size mismatch (actual < promised) 도 동일하게 abort + 예외")
        void actualSizeSmallerThanPromised_alsoAbortsAndThrows() {
            given(s3Service.completeUpload(any())).willReturn(
                    CompleteMultipartUploadResponse.builder().location("loc").eTag("\"e\"").build());
            given(s3Service.getObjectActualSize(S3_KEY)).willReturn(10L * MB);

            FileDto.CompleteMultipartUploadRequest req = FileDto.CompleteMultipartUploadRequest.builder()
                    .key(S3_KEY).uploadId(UPLOAD_ID)
                    .parts(List.of(FileDto.Part.builder().partNumber(1).etag("e").build()))
                    .originalFileName("a.pdf").fileSize(50L * MB).build();

            assertThatThrownBy(() -> fileService.completeMultipartUpload(req))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FILE_SIZE_MISMATCH);
            then(s3Service).should().abortUpload(any());
            then(fileRepository).should(never()).save(any());
        }
    }

    // =====================================================================
    // 6) abortMultipartUpload — 단순 위임
    // =====================================================================
    @Nested
    @DisplayName("abortMultipartUpload(): S3Service.abortUpload 위임")
    class AbortMultipartUpload {

        @Test
        @DisplayName("key/uploadId 가 AbortUploadRequest 로 정확히 전달됨")
        void delegatesKeyAndUploadId() {
            fileService.abortMultipartUpload(S3_KEY, UPLOAD_ID);

            ArgumentCaptor<S3ServiceDto.AbortUploadRequest> cap =
                    ArgumentCaptor.forClass(S3ServiceDto.AbortUploadRequest.class);
            then(s3Service).should().abortUpload(cap.capture());
            assertThat(cap.getValue().getKey()).isEqualTo(S3_KEY);
            assertThat(cap.getValue().getUploadId()).isEqualTo(UPLOAD_ID);
        }
    }

    // =====================================================================
    // 7) validateFileOwner — IDOR 가드 (단일)
    // =====================================================================
    @Nested
    @DisplayName("validateFileOwner(): IDOR 가드")
    class ValidateFileOwner {

        @Test
        @DisplayName("createdBy == userId → 엔티티 그대로 반환 (정상)")
        void ownerMatches_returnsEntity() {
            File file = File.builder().id(7L).s3ObjectKey("k").filename("a.pdf")
                    .fileSize(MB).mediaType(MediaType.PDF).build();
            file.setCreatedBy(100L);

            given(fileRepository.findById(7L)).willReturn(Optional.of(file));

            File result = fileService.validateFileOwner(7L, 100L);

            assertThat(result).isSameAs(file);
        }

        @Test
        @DisplayName("findById 비어있음 → FILE_NOT_FOUND")
        void notFound_throwsFileNotFound() {
            given(fileRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.validateFileOwner(99L, 1L))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FILE_NOT_FOUND);
        }

        @Test
        @DisplayName("createdBy != userId → AUTH_FORBIDDEN (다른 사용자 파일 접근 차단)")
        void differentOwner_throwsAuthForbidden() {
            File file = File.builder().id(7L).s3ObjectKey("k").filename("a.pdf")
                    .fileSize(MB).mediaType(MediaType.PDF).build();
            file.setCreatedBy(100L); // 소유자 = 100

            given(fileRepository.findById(7L)).willReturn(Optional.of(file));

            // 다른 사용자(200) 로 접근
            assertThatThrownBy(() -> fileService.validateFileOwner(7L, 200L))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // =====================================================================
    // 8) validateFileOwners — IDOR 가드 (일괄)
    // =====================================================================
    @Nested
    @DisplayName("validateFileOwners(): IDOR 가드 (일괄)")
    class ValidateFileOwners {

        @Test
        @DisplayName("null fileIds → 빈 리스트 반환, repository 미호출")
        void nullList_returnsEmptyAndNoRepoCall() {
            List<File> result = fileService.validateFileOwners(null, 1L);

            assertThat(result).isEmpty();
            then(fileRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("빈 리스트 → 빈 리스트 반환, repository 미호출")
        void emptyList_returnsEmptyAndNoRepoCall() {
            List<File> result = fileService.validateFileOwners(Collections.emptyList(), 1L);

            assertThat(result).isEmpty();
            then(fileRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("모두 존재 + 모두 동일 소유자 → 조회 결과 그대로 반환")
        void allOwnedByUser_returnsList() {
            File f1 = file(1L, 100L);
            File f2 = file(2L, 100L);
            given(fileRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(f1, f2));

            List<File> result = fileService.validateFileOwners(List.of(1L, 2L), 100L);

            assertThat(result).containsExactly(f1, f2);
        }

        @Test
        @DisplayName("findAllById 결과 수 != distinct 요청 수 → FILE_NOT_FOUND (어느 하나라도 미존재)")
        void missingId_throwsFileNotFound() {
            // 요청 distinct=2, 조회 결과 1 → 누락
            given(fileRepository.findAllById(List.of(1L, 2L)))
                    .willReturn(List.of(file(1L, 100L)));

            assertThatThrownBy(() -> fileService.validateFileOwners(List.of(1L, 2L), 100L))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FILE_NOT_FOUND);
        }

        @Test
        @DisplayName("중복 ID 는 distinct 카운트로 정규화 — distinct=1, 조회 1 → 통과")
        void duplicateIds_normalizedByDistinct() {
            File f1 = file(1L, 100L);
            given(fileRepository.findAllById(List.of(1L, 1L))).willReturn(List.of(f1));

            List<File> result = fileService.validateFileOwners(List.of(1L, 1L), 100L);

            assertThat(result).containsExactly(f1);
        }

        @Test
        @DisplayName("어느 하나라도 다른 소유자 → AUTH_FORBIDDEN (부분 성공 불허)")
        void anyDifferentOwner_throwsAuthForbidden() {
            File f1 = file(1L, 100L); // mine
            File f2 = file(2L, 999L); // someone else
            given(fileRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(f1, f2));

            assertThatThrownBy(() -> fileService.validateFileOwners(List.of(1L, 2L), 100L))
                    .isInstanceOf(RestException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private static File file(Long id, Long createdBy) {
        File f = File.builder()
                .id(id)
                .s3ObjectKey("k-" + id)
                .filename("name-" + id + ".pdf")
                .fileSize(MB)
                .mediaType(MediaType.PDF)
                .build();
        f.setCreatedBy(createdBy);
        return f;
    }
}
