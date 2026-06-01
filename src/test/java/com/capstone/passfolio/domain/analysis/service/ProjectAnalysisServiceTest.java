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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Optional;

import com.capstone.passfolio.domain.ai.client.AiApiClient;
import com.capstone.passfolio.domain.ai.dto.AiDto;
import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.domain.aws.sqs.SqsMessageSender;
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

    @InjectMocks
    private ProjectAnalysisService projectAnalysisService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(projectAnalysisService, "analysisQueueUrl", "https://sqs/q");
        ReflectionTestUtils.setField(projectAnalysisService, "maxRepoSizeKb", 1_048_576L); // 1GiB
    }

    // ---------- 배치 디스패치 ----------

    private ProjectAnalysisDto.StartRequest startReq(List<String> repoUrls) {
        return new ProjectAnalysisDto.StartRequest(repoUrls, "NONSTOP", null);
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
        then(aiApiClient).should(never()).requestPortfolioFromAnalyses(any()); // 실패 포함 → 핸드오프 X
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
    @DisplayName("배치 all-done 전원성공 + NONSTOP → FastAPI 핸드오프 + 배치 SSE + SMS (커밋 후)")
    void complete_batch_all_success_handoff() {
        given(projectAnalysisWriter.applyCompletion(any())).willReturn(result("b1", false, "NONSTOP"));
        given(batchProgressTracker.recordTerminal("b1", false))
                .willReturn(new BatchProgressTracker.BatchOutcome(true, true, 0));
        ProjectAnalysis done = ProjectAnalysis.builder()
                .id("a1").batchId("b1").repoUrl("repo").analysisFlag(AnalysisFlag.DONE)
                .resultCdnUrl("https://cdn.x/r.json").serviceName("Svc").build();
        given(projectAnalysisRepository.findByBatchId("b1")).willReturn(List.of(done));

        projectAnalysisService.completeAnalysis(req("a1", "analyzed", "https://cdn.x/r.json", "Svc", null));

        then(aiApiClient).should().requestPortfolioFromAnalyses(any(AiDto.AnalysisResultsRequest.class));
        then(sseService).should().pushBatch(eq(7L), any());
        then(smsNotifier).should().notifyBatchCompleted(eq(7L), eq("b1"), eq(1), eq(true));
    }

    @Test
    @DisplayName("배치 all-done 실패 포함 → FastAPI 핸드오프 생략, 배치 SSE")
    void complete_batch_with_failure_no_handoff() {
        given(projectAnalysisWriter.applyCompletion(any())).willReturn(result("b1", true, "NONSTOP"));
        given(batchProgressTracker.recordTerminal("b1", true))
                .willReturn(new BatchProgressTracker.BatchOutcome(true, false, 1));
        given(projectAnalysisRepository.findByBatchId("b1")).willReturn(List.of());

        projectAnalysisService.completeAnalysis(req("a1", "failed", null, null, "boom"));

        then(aiApiClient).should(never()).requestPortfolioFromAnalyses(any());
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
}
