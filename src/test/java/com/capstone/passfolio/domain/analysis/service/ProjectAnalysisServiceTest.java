package com.capstone.passfolio.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.Optional;

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
 * 디스패치(initiateAnalysis)와 완료 콜백(completeAnalysis)의 매핑·게이트·멱등성을 검증한다.
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

    @InjectMocks
    private ProjectAnalysisService projectAnalysisService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(projectAnalysisService, "analysisQueueUrl", "https://sqs/q");
        ReflectionTestUtils.setField(projectAnalysisService, "maxRepoSizeKb", 1_048_576L); // 1GiB
    }

    // ---------- 디스패치 ----------

    private ProjectAnalysisDto.StartRequest startReq() {
        return new ProjectAnalysisDto.StartRequest("https://github.com/owner/repo", "NONSTOP");
    }

    // 공통 스텁: size 게이트 직전까지(모든 디스패치 경로에서 사용).
    private void stubCore(int sizeKb) {
        User user = mock(User.class);
        given(user.getGithubLogin()).willReturn("octocat");
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(tokenPreparer.resolvePlaintextWithTtlCheck(7L)).willReturn("ghp_plain");
        GitHubDto.ApiRepo repo = mock(GitHubDto.ApiRepo.class);
        given(repo.getSize()).willReturn(sizeKb);
        given(gitHubApiClient.fetchRepo("ghp_plain", "owner", "repo")).willReturn(repo);
    }

    @Test
    @DisplayName("정상 디스패치 → SQS 전송 + IN_PROGRESS 반환 (토큰→size→admission→TTL재점검→KMS 순)")
    void dispatch_success() {
        stubCore(100);
        given(tokenPreparer.reencryptForLambda("ghp_plain")).willReturn("kms-cipher");

        ProjectAnalysisDto.StartResponse res =
                projectAnalysisService.initiateAnalysis(7L, startReq());

        assertThat(res.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(res.getAnalysisId()).isNotBlank();
        then(admissionPacer).should().acquireOrThrow();
        then(tokenPreparer).should().assertSufficientTtl(7L);          // 전송 직전 재점검
        then(projectAnalysisWriter).should().createYet(anyString(), eq("https://github.com/owner/repo"), any());
        then(sqsMessageSender).should(times(1)).send(eq("https://sqs/q"), any(ProjectAnalysisDto.LambdaJobMessage.class));
        then(projectAnalysisWriter).should().markInProgress(anyString());
    }

    @Test
    @DisplayName("repo size 초과 → ANALYSIS_REPO_SIZE_EXCEEDED, SQS 미전송")
    void dispatch_repo_too_large() {
        stubCore(2_000_000); // > 1GiB

        assertThatThrownBy(() -> projectAnalysisService.initiateAnalysis(7L, startReq()))
                .isInstanceOf(RestException.class)
                .extracting(e -> ((RestException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_REPO_SIZE_EXCEEDED);

        then(admissionPacer).should(never()).acquireOrThrow();
        then(sqsMessageSender).should(never()).send(anyString(), any());
    }

    @Test
    @DisplayName("SQS 전송 실패 → markFailed + ANALYSIS_DISPATCH_FAILED")
    void dispatch_sqs_failure() {
        stubCore(100);
        given(tokenPreparer.reencryptForLambda("ghp_plain")).willReturn("kms-cipher");
        willThrow(new RuntimeException("sqs down"))
                .given(sqsMessageSender).send(anyString(), any());

        assertThatThrownBy(() -> projectAnalysisService.initiateAnalysis(7L, startReq()))
                .isInstanceOf(RestException.class)
                .extracting(e -> ((RestException) e).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_DISPATCH_FAILED);

        then(projectAnalysisWriter).should().markFailed(anyString(), anyString());
        then(projectAnalysisWriter).should(never()).markInProgress(anyString());
    }

    // ---------- 완료 콜백 ----------

    private ProjectAnalysis inProgress(String id) {
        return ProjectAnalysis.builder()
                .id(id)
                .repoUrl("https://github.com/o/r.git")
                .analysisFlag(AnalysisFlag.IN_PROGRESS)
                .build();
    }

    private ProjectAnalysisDto.WebhookCompleteRequest req(
            String id, String status, String cdnUrl, String serviceName, String errorMessage) {
        return new ProjectAnalysisDto.WebhookCompleteRequest(id, status, cdnUrl, serviceName, errorMessage);
    }

    @Test
    @DisplayName("analyzed + cdnUrl → DONE, cdn/service 저장")
    void complete_success() {
        ProjectAnalysis a = inProgress("a1");
        given(projectAnalysisRepository.findById("a1")).willReturn(Optional.of(a));

        projectAnalysisService.completeAnalysis(
                req("a1", "analyzed", "https://cdn.x/r.json", "MyService", null));

        assertThat(a.getAnalysisFlag()).isEqualTo(AnalysisFlag.DONE);
        assertThat(a.getResultCdnUrl()).isEqualTo("https://cdn.x/r.json");
        assertThat(a.getServiceName()).isEqualTo("MyService");
    }

    @Test
    @DisplayName("성공인데 cdnUrl 없음 → FAILED 강제")
    void complete_success_without_cdn_forces_failed() {
        ProjectAnalysis a = inProgress("a1");
        given(projectAnalysisRepository.findById("a1")).willReturn(Optional.of(a));

        projectAnalysisService.completeAnalysis(req("a1", "analyzed", "  ", null, null));

        assertThat(a.getAnalysisFlag()).isEqualTo(AnalysisFlag.FAILED);
        assertThat(a.getResultCdnUrl()).isNull();
    }

    @Test
    @DisplayName("failed 상태 → FAILED, 사유 저장")
    void complete_failure() {
        ProjectAnalysis a = inProgress("a1");
        given(projectAnalysisRepository.findById("a1")).willReturn(Optional.of(a));

        projectAnalysisService.completeAnalysis(req("a1", "failed", null, null, "clone error"));

        assertThat(a.getAnalysisFlag()).isEqualTo(AnalysisFlag.FAILED);
        assertThat(a.getFailureReason()).isEqualTo("clone error");
    }

    @Test
    @DisplayName("이미 종료(DONE) → 멱등 skip(상태 불변)")
    void complete_idempotent_skip() {
        ProjectAnalysis a = ProjectAnalysis.builder()
                .id("a1").repoUrl("r").analysisFlag(AnalysisFlag.DONE).build();
        given(projectAnalysisRepository.findById("a1")).willReturn(Optional.of(a));

        projectAnalysisService.completeAnalysis(req("a1", "failed", null, null, "late failure"));

        assertThat(a.getAnalysisFlag()).isEqualTo(AnalysisFlag.DONE);
        assertThat(a.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 분석 → PROJECT_ANALYSIS_NOT_FOUND")
    void complete_not_found() {
        given(projectAnalysisRepository.findById("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> projectAnalysisService.completeAnalysis(
                req("missing", "analyzed", "https://cdn.x/r.json", "S", null)))
                .isInstanceOf(RestException.class)
                .extracting(e -> ((RestException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ANALYSIS_NOT_FOUND);
    }
}
