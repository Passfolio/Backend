package com.capstone.passfolio.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;

import com.capstone.passfolio.common.util.FileUrlUtils;
import com.capstone.passfolio.domain.ai.client.AiApiClient;
import com.capstone.passfolio.domain.ai.dto.AiDto;
import com.capstone.passfolio.domain.ai.service.AiJobService;
import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.domain.aws.sqs.SqsMessageSender;
import com.capstone.passfolio.domain.file.entity.File;
import com.capstone.passfolio.domain.file.entity.enums.DocumentType;
import com.capstone.passfolio.domain.file.service.FileService;
import com.capstone.passfolio.domain.github.client.GitHubApiClient;
import com.capstone.passfolio.domain.github.dto.GitHubDto;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link ProjectAnalysisService} 단위 테스트. Spring 컨텍스트 0개, 협력자는 Mockito mock.
 * 배치 디스패치 + 완료 콜백(개별 SSE·배치 all-done 핸드오프)·멱등성을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectAnalysisServiceTest {

    @Mock private ProjectAnalysisRepository projectAnalysisRepository;
    @Mock private ProjectAnalysisWriter projectAnalysisWriter;
    @Mock private UserRepository userRepository;
    @Mock private GitHubApiClient gitHubApiClient;
    @Mock private AnalysisTokenPreparer tokenPreparer;
    @Mock private AnalysisAdmissionPacer admissionPacer;
    @Mock private SqsMessageSender sqsMessageSender;
    @Mock private BatchProgressTracker batchProgressTracker;
    @Mock private BatchPhoneStore batchPhoneStore;
    @Mock private ProjectAnalysisSseService sseService;
    @Mock private AiApiClient aiApiClient;
    @Mock private SmsNotifier smsNotifier;
    @Mock private AiJobService aiJobService;
    @Mock private BatchPortfolioStore batchPortfolioStore;
    @Mock private RepoAvailabilityService repoAvailabilityService;
    @Mock private FileService fileService;
    @Mock private AnalysisArtifactPresigner analysisArtifactPresigner; // 동일 패키지 → import 불필요
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private ProjectAnalysisService projectAnalysisService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(projectAnalysisService, "analysisQueueUrl", "https://sqs/q");
        ReflectionTestUtils.setField(projectAnalysisService, "maxRepoSizeKb", 1_048_576L); // 1GiB
        // FileUrlUtils.buildCdnUrl는 정적 cdnBaseUrl을 읽음 — NONSTOP fileId→CDN URL 변환 테스트용 주입.
        ReflectionTestUtils.setField(FileUrlUtils.class, "cdnBaseUrl", "https://cdn.x");
        // presign은 봇룰 우회용 URL 변환(별도 단위테스트 책임) — 핸드오프 오케스트레이션 검증에선 항등 취급.
        lenient().when(analysisArtifactPresigner.presign(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- 배치 디스패치 ----------

    // 디스패치 메커닉 검증용 — 모드는 디스패치에 무관하므로 STEP(포폴 핸드오프 경로 비활성).
    private ProjectAnalysisDto.StartRequest startReq(List<String> repoUrls) {
        return new ProjectAnalysisDto.StartRequest(repoUrls, "STEP", null, null, null);
    }

    // NONSTOP 포폴 입력 검증용.
    private ProjectAnalysisDto.StartRequest nonstopReq(List<String> repoUrls, Long fileId, String purpose) {
        return new ProjectAnalysisDto.StartRequest(repoUrls, "NONSTOP", null, fileId, purpose);
    }

    // resolvePortfolioPdfUrl 도달 전까지의 최소 스텁(user 로드 + githubLogin).
    private void stubUserOnly() {
        User user = mock(User.class);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(user.getGithubLogin()).willReturn("octocat");
    }

    // size 게이트 직전까지 공통 스텁(단일 repo).
    private void stubCoreSingle(int sizeKb) {
        User user = mock(User.class);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(user.getGithubLogin()).willReturn("octocat");
        given(tokenPreparer.resolvePlaintextWithTtlCheck(7L)).willReturn("ghp_plain");
        GitHubDto.ApiRepo repo = mock(GitHubDto.ApiRepo.class);
        given(repo.getSize()).willReturn(sizeKb);
        given(gitHubApiClient.fetchRepo("ghp_plain", "owner", "repo")).willReturn(repo);
    }

    @Test
    @DisplayName("단일 repo 배치 → SQS 전송 + batchId + IN_PROGRESS")
    void dispatch_single_success() {
        stubCoreSingle(100);
        given(tokenPreparer.reencryptForLambda("ghp_plain")).willReturn("enc");

        ProjectAnalysisDto.StartResponse res = projectAnalysisService.initiateBatch(
                7L, startReq(List.of("https://github.com/owner/repo")));

        assertThat(res.getBatchId()).isNotBlank();
        assertThat(res.getAnalyses()).hasSize(1);
        assertThat(res.getAnalyses().get(0).getStatus()).isEqualTo("IN_PROGRESS");
        then(admissionPacer).should().acquireOrThrow(1);
        then(batchProgressTracker).should().createBatch(anyString(), eq(1));
        then(sqsMessageSender).should(times(1)).send(eq("https://sqs/q"), any());
    }

    @Test
    @DisplayName("배치 4개 → ANALYSIS_BATCH_SIZE_EXCEEDED")
    void dispatch_batch_too_many() {
        assertThatThrownBy(() -> projectAnalysisService.initiateBatch(7L, startReq(
                List.of("https://github.com/o/a", "https://github.com/o/b",
                        "https://github.com/o/c", "https://github.com/o/d"))))
                .isInstanceOf(RestException.class)
                .extracting(e -> ((RestException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_BATCH_SIZE_EXCEEDED);
        then(sqsMessageSender).should(never()).send(anyString(), any());
    }

    @Test
    @DisplayName("repo size 초과 → ANALYSIS_REPO_SIZE_EXCEEDED, admission/SQS 미수행")
    void dispatch_repo_too_large() {
        stubCoreSingle(2_000_000); // > 1GiB

        assertThatThrownBy(() -> projectAnalysisService.initiateBatch(
                7L, startReq(List.of("https://github.com/owner/repo"))))
                .isInstanceOf(RestException.class)
                .extracting(e -> ((RestException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_REPO_SIZE_EXCEEDED);
        then(admissionPacer).should(never()).acquireOrThrow(anyInt());
        then(sqsMessageSender).should(never()).send(anyString(), any());
    }

    @Test
    @DisplayName("SQS 전송 실패 → 해당 repo FAILED(예외 전파 X), 배치 종료 처리")
    void dispatch_sqs_failure_marks_failed() {
        stubCoreSingle(100);
        given(tokenPreparer.reencryptForLambda("ghp_plain")).willReturn("enc");
        willThrow(new RuntimeException("sqs down")).given(sqsMessageSender).send(anyString(), any());
        // 단일 repo가 디스패치 실패로 즉시 all-done(전원 실패)
        given(batchProgressTracker.recordTerminal(anyString(), eq(true)))
                .willReturn(new BatchProgressTracker.BatchOutcome(true, false, 1));
        given(projectAnalysisRepository.findByBatchId(anyString())).willReturn(List.of());

        ProjectAnalysisDto.StartResponse res = projectAnalysisService.initiateBatch(
                7L, startReq(List.of("https://github.com/owner/repo")));

        assertThat(res.getAnalyses().get(0).getStatus()).isEqualTo("FAILED");
        then(projectAnalysisWriter).should().markFailed(anyString(), anyString());
        then(aiJobService).should(never()).startPortfolioFromAnalyses(any(), any(), any(), any()); // 실패 포함 → 핸드오프 X
        then(sseService).should().pushBatch(eq(7L), any());
    }

    // ---------- 완료 콜백 (DB는 writer가 커밋, service는 커밋 후 통지/배치 오케스트레이션) ----------

    private ProjectAnalysisDto.WebhookCompleteRequest req(
            String id, String status, String cdnUrl, String serviceName, String errorMessage) {
        return new ProjectAnalysisDto.WebhookCompleteRequest(id, status, cdnUrl, serviceName, errorMessage);
    }

    private ProjectAnalysisWriter.CompletionResult result(String batchId, boolean failed, String mode) {
        return new ProjectAnalysisWriter.CompletionResult(
                "a1", batchId, 7L, mode, failed, "repo", "https://cdn.x/r.json", "Svc",
                failed ? "FAILED" : "DONE");
    }

    @Test
    @DisplayName("성공 콜백 → 개별 SSE (batch 없음 → 카운터 미수행)")
    void complete_pushes_individual_sse() {
        given(projectAnalysisWriter.applyCompletion(any())).willReturn(result(null, false, "NONSTOP"));

        projectAnalysisService.completeAnalysis(req("a1", "analyzed", "https://cdn.x/r.json", "Svc", null));

        then(sseService).should().pushAnalysis(eq(7L), any());
        then(batchProgressTracker).should(never())
                .recordTerminal(anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("배치 all-done 전원성공 + NONSTOP + 포폴 PDF/목적 있음 → 포폴 핸드오프(startPortfolioFromAnalyses) + linkJob + 배치 SSE + SMS")
    void complete_batch_all_success_handoff() {
        given(projectAnalysisWriter.applyCompletion(any())).willReturn(result("b1", false, "NONSTOP"));
        given(batchProgressTracker.recordTerminal("b1", false))
                .willReturn(new BatchProgressTracker.BatchOutcome(true, true, 0));
        ProjectAnalysis done = ProjectAnalysis.builder()
                .id("a1").batchId("b1").repoUrl("repo").analysisFlag(AnalysisFlag.DONE)
                .resultCdnUrl("https://cdn.x/r.json").serviceName("Svc").build();
        given(projectAnalysisRepository.findByBatchId("b1")).willReturn(List.of(done));
        given(batchPortfolioStore.readPdfUrl("b1")).willReturn("https://cdn.x/upload.pdf");
        given(batchPortfolioStore.readPurpose("b1")).willReturn("EDIT");
        given(aiJobService.startPortfolioFromAnalyses(eq(7L), eq("https://cdn.x/upload.pdf"), eq("EDIT"), any()))
                .willReturn(99L);

        projectAnalysisService.completeAnalysis(req("a1", "analyzed", "https://cdn.x/r.json", "Svc", null));

        then(aiJobService).should().startPortfolioFromAnalyses(
                eq(7L), eq("https://cdn.x/upload.pdf"), eq("EDIT"), eq(List.of("https://cdn.x/r.json")));
        then(batchPortfolioStore).should().linkJob("b1", 99L);
        then(sseService).should().pushBatch(eq(7L), any());
        then(smsNotifier).should().notifyBatchCompleted(eq(7L), eq("b1"), eq(1), eq(true));
    }

    @Test
    @DisplayName("배치 all-done 전원성공 + NONSTOP인데 포폴 PDF 미지정 → 핸드오프 생략")
    void complete_batch_all_success_no_pdf_skips_handoff() {
        given(projectAnalysisWriter.applyCompletion(any())).willReturn(result("b1", false, "NONSTOP"));
        given(batchProgressTracker.recordTerminal("b1", false))
                .willReturn(new BatchProgressTracker.BatchOutcome(true, true, 0));
        given(projectAnalysisRepository.findByBatchId("b1")).willReturn(List.of());
        given(batchPortfolioStore.readPdfUrl("b1")).willReturn(null);

        projectAnalysisService.completeAnalysis(req("a1", "analyzed", "https://cdn.x/r.json", "Svc", null));

        then(aiJobService).should(never()).startPortfolioFromAnalyses(any(), any(), any(), any());
        then(sseService).should().pushBatch(eq(7L), any());
    }

    @Test
    @DisplayName("배치 all-done 실패 포함 → FastAPI 핸드오프 생략, 배치 SSE")
    void complete_batch_with_failure_no_handoff() {
        given(projectAnalysisWriter.applyCompletion(any())).willReturn(result("b1", true, "NONSTOP"));
        given(batchProgressTracker.recordTerminal("b1", true))
                .willReturn(new BatchProgressTracker.BatchOutcome(true, false, 1));
        given(projectAnalysisRepository.findByBatchId("b1")).willReturn(List.of());

        projectAnalysisService.completeAnalysis(req("a1", "failed", null, null, "boom"));

        then(aiJobService).should(never()).startPortfolioFromAnalyses(any(), any(), any(), any());
        then(sseService).should().pushBatch(eq(7L), any());
    }

    @Test
    @DisplayName("이미 종료 → writer가 null 반환 → 통지·카운터 미수행(멱등)")
    void complete_idempotent_skip() {
        given(projectAnalysisWriter.applyCompletion(any())).willReturn(null);

        projectAnalysisService.completeAnalysis(req("a1", "failed", null, null, "late"));

        then(sseService).should(never()).pushAnalysis(any(), any());
        then(batchProgressTracker).should(never())
                .recordTerminal(anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("미존재 → PROJECT_ANALYSIS_NOT_FOUND 전파")
    void complete_not_found() {
        willThrow(new RestException(ErrorCode.PROJECT_ANALYSIS_NOT_FOUND))
                .given(projectAnalysisWriter).applyCompletion(any());

        assertThatThrownBy(() -> projectAnalysisService.completeAnalysis(
                req("missing", "analyzed", "https://cdn.x/r.json", "S", null)))
                .isInstanceOf(RestException.class)
                .extracting(e -> ((RestException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ANALYSIS_NOT_FOUND);
    }

    // ---------- NONSTOP 포폴 입력 검증 (Fix 1: fileId 소유권 + 문서종류 정렬) ----------

    @Test
    @DisplayName("NONSTOP인데 fileId 미지정 → ANALYSIS_PORTFOLIO_INPUT_REQUIRED, 디스패치 X")
    void nonstop_requires_file() {
        stubUserOnly();
        assertThatThrownBy(() -> projectAnalysisService.initiateBatch(
                7L, nonstopReq(List.of("https://github.com/owner/repo"), null, "EDIT")))
                .isInstanceOf(RestException.class)
                .extracting(e -> ((RestException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_PORTFOLIO_INPUT_REQUIRED);
        then(sqsMessageSender).should(never()).send(anyString(), any());
    }

    @Test
    @DisplayName("NONSTOP GENERATE인데 업로드가 PORTFOLIO 문서 → ANALYSIS_PORTFOLIO_FILE_TYPE_MISMATCH")
    void nonstop_file_type_mismatch() {
        stubUserOnly();
        File file = mock(File.class);
        given(fileService.validateFileOwner(5L, 7L)).willReturn(file);
        given(file.getDocumentType()).willReturn(DocumentType.PORTFOLIO); // GENERATE는 COVER_LETTER여야 함

        assertThatThrownBy(() -> projectAnalysisService.initiateBatch(
                7L, nonstopReq(List.of("https://github.com/owner/repo"), 5L, "GENERATE")))
                .isInstanceOf(RestException.class)
                .extracting(e -> ((RestException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_PORTFOLIO_FILE_TYPE_MISMATCH);
        then(sqsMessageSender).should(never()).send(anyString(), any());
    }

    @Test
    @DisplayName("NONSTOP EDIT + PORTFOLIO 파일 → 소유권검증 후 서버 CDN URL 생성·저장 + 디스패치")
    void nonstop_resolves_cdn_and_dispatches() {
        stubCoreSingle(100);
        given(tokenPreparer.reencryptForLambda("ghp_plain")).willReturn("enc");
        File file = mock(File.class);
        given(fileService.validateFileOwner(5L, 7L)).willReturn(file);
        given(file.getDocumentType()).willReturn(DocumentType.PORTFOLIO);
        given(file.getS3ObjectKey()).willReturn("uploads/p.pdf");

        ProjectAnalysisDto.StartResponse res = projectAnalysisService.initiateBatch(
                7L, nonstopReq(List.of("https://github.com/owner/repo"), 5L, "EDIT"));

        assertThat(res.getAnalyses().get(0).getStatus()).isEqualTo("IN_PROGRESS");
        // 원시 URL 통과가 아니라 소유 파일의 s3ObjectKey로 서버가 생성한 CDN URL을 저장.
        then(batchPortfolioStore).should().store(anyString(),
                org.mockito.ArgumentMatchers.contains("uploads/p.pdf"), eq("EDIT"));
        then(sqsMessageSender).should().send(eq("https://sqs/q"), any());
    }

    // ---------- 핸드오프 실패 가시화 (Fix 3) ----------

    @Test
    @DisplayName("핸드오프 실패 → portfolioFailed SSE + notifyPortfolioHandoffFailed (배치 완료 SMS 대신)")
    void complete_handoff_failure_visualized() {
        given(projectAnalysisWriter.applyCompletion(any())).willReturn(result("b1", false, "NONSTOP"));
        given(batchProgressTracker.recordTerminal("b1", false))
                .willReturn(new BatchProgressTracker.BatchOutcome(true, true, 0));
        ProjectAnalysis done = ProjectAnalysis.builder()
                .id("a1").batchId("b1").repoUrl("repo").analysisFlag(AnalysisFlag.DONE)
                .resultCdnUrl("https://cdn.x/r.json").build();
        given(projectAnalysisRepository.findByBatchId("b1")).willReturn(List.of(done));
        given(batchPortfolioStore.readPdfUrl("b1")).willReturn("https://cdn.x/u.pdf");
        given(batchPortfolioStore.readPurpose("b1")).willReturn("EDIT");
        willThrow(new RestException(ErrorCode.AI_SERVER_UNAVAILABLE))
                .given(aiJobService).startPortfolioFromAnalyses(any(), any(), any(), any());

        projectAnalysisService.completeAnalysis(req("a1", "analyzed", "https://cdn.x/r.json", "Svc", null));

        then(smsNotifier).should().notifyPortfolioHandoffFailed(7L, "b1");
        then(smsNotifier).should(never())
                .notifyBatchCompleted(any(), anyString(), anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
        then(sseService).should().pushBatch(eq(7L), any());
    }

    // ---------- 수동 재시도 (Fix 3) ----------

    @Test
    @DisplayName("재시도: 소유 배치 + 전원성공 + 미핸드오프 → 핸드오프 + linkJob + SSE")
    void retry_success() {
        ProjectAnalysis done = ProjectAnalysis.builder()
                .id("a1").batchId("b1").repoUrl("repo").analysisFlag(AnalysisFlag.DONE)
                .resultCdnUrl("https://cdn.x/r.json").build();
        given(projectAnalysisRepository.findByBatchIdAndUser_Id("b1", 7L)).willReturn(List.of(done));
        given(batchPortfolioStore.readJobByBatch("b1")).willReturn(null);
        given(batchPortfolioStore.readPdfUrl("b1")).willReturn("https://cdn.x/u.pdf");
        given(batchPortfolioStore.readPurpose("b1")).willReturn("EDIT");
        given(aiJobService.startPortfolioFromAnalyses(eq(7L), eq("https://cdn.x/u.pdf"), eq("EDIT"), any()))
                .willReturn(42L);

        projectAnalysisService.retryPortfolioHandoff(7L, "b1");

        then(batchPortfolioStore).should().linkJob("b1", 42L);
        then(sseService).should().pushBatch(eq(7L), any());
    }

    @Test
    @DisplayName("재시도: 이미 핸드오프된 배치 → ANALYSIS_PORTFOLIO_HANDOFF_NOT_RETRYABLE")
    void retry_already_handed_off() {
        ProjectAnalysis done = ProjectAnalysis.builder()
                .id("a1").batchId("b1").analysisFlag(AnalysisFlag.DONE).build();
        given(projectAnalysisRepository.findByBatchIdAndUser_Id("b1", 7L)).willReturn(List.of(done));
        given(batchPortfolioStore.readJobByBatch("b1")).willReturn(50L);

        assertThatThrownBy(() -> projectAnalysisService.retryPortfolioHandoff(7L, "b1"))
                .isInstanceOf(RestException.class)
                .extracting(e -> ((RestException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_PORTFOLIO_HANDOFF_NOT_RETRYABLE);
        then(aiJobService).should(never()).startPortfolioFromAnalyses(any(), any(), any(), any());
    }

    @Test
    @DisplayName("재시도: 타인/미존재 배치 → PROJECT_ANALYSIS_NOT_FOUND")
    void retry_not_owned() {
        given(projectAnalysisRepository.findByBatchIdAndUser_Id("bX", 7L)).willReturn(List.of());

        assertThatThrownBy(() -> projectAnalysisService.retryPortfolioHandoff(7L, "bX"))
                .isInstanceOf(RestException.class)
                .extracting(e -> ((RestException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ANALYSIS_NOT_FOUND);
    }

    // ---------- 핸드오프 in-flight 레이스: retryable 오탐 방지 ----------

    private ProjectAnalysis doneRepo() {
        return ProjectAnalysis.builder()
                .id("a1").batchId("b1").repoUrl("r").analysisFlag(AnalysisFlag.DONE)
                .resultCdnUrl("https://cdn.x/r.json").build();
    }

    @Test
    @DisplayName("핸드오프 in-flight(FastAPI 호출 중) → portfolioRetryable=false (오탐 방지)")
    void batch_status_handoff_inflight_not_retryable() {
        given(projectAnalysisRepository.findByBatchIdAndUser_Id("b1", 7L)).willReturn(List.of(doneRepo()));
        given(batchPortfolioStore.readJobByBatch("b1")).willReturn(null);          // 아직 미링크
        given(batchPortfolioStore.readPdfUrl("b1")).willReturn("https://cdn.x/u.pdf");
        given(batchPortfolioStore.isHandoffInProgress("b1")).willReturn(true);     // in-flight

        ProjectAnalysisDto.AdminBatchStatusResponse res = projectAnalysisService.getUserBatchStatus(7L, "b1");

        assertThat(res.isPortfolioRetryable()).isFalse();
        assertThat(res.getPortfolioJobId()).isNull();
    }

    @Test
    @DisplayName("핸드오프 종료·미링크(진짜 실패) → portfolioRetryable=true")
    void batch_status_handoff_failed_retryable() {
        given(projectAnalysisRepository.findByBatchIdAndUser_Id("b1", 7L)).willReturn(List.of(doneRepo()));
        given(batchPortfolioStore.readJobByBatch("b1")).willReturn(null);
        given(batchPortfolioStore.readPdfUrl("b1")).willReturn("https://cdn.x/u.pdf");
        given(batchPortfolioStore.isHandoffInProgress("b1")).willReturn(false);    // 종료됨 + 미링크 → 실패

        ProjectAnalysisDto.AdminBatchStatusResponse res = projectAnalysisService.getUserBatchStatus(7L, "b1");

        assertThat(res.isPortfolioRetryable()).isTrue();
    }

    @Test
    @DisplayName("complete all-done 핸드오프: in-flight mark/clear 호출(레이스 창 보호)")
    void complete_handoff_marks_and_clears_inflight() {
        given(projectAnalysisWriter.applyCompletion(any())).willReturn(result("b1", false, "NONSTOP"));
        given(batchProgressTracker.recordTerminal("b1", false))
                .willReturn(new BatchProgressTracker.BatchOutcome(true, true, 0));
        given(projectAnalysisRepository.findByBatchId("b1")).willReturn(List.of(doneRepo()));
        given(batchPortfolioStore.readPdfUrl("b1")).willReturn("https://cdn.x/u.pdf");
        given(batchPortfolioStore.readPurpose("b1")).willReturn("EDIT");
        given(aiJobService.startPortfolioFromAnalyses(eq(7L), any(), eq("EDIT"), any())).willReturn(99L);

        projectAnalysisService.completeAnalysis(req("a1", "analyzed", "https://cdn.x/r.json", "Svc", null));

        then(batchPortfolioStore).should().markHandoffInProgress("b1");
        then(batchPortfolioStore).should().clearHandoffInProgress("b1");
        then(batchPortfolioStore).should().linkJob("b1", 99L);
    }
}
