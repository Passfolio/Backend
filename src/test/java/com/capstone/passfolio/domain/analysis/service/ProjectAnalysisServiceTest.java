package com.capstone.passfolio.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ProjectAnalysisService} 단위 테스트. Spring 컨텍스트 0개, repository는 Mockito mock.
 * Lambda 완료 콜백의 매핑·멱등성·실패 처리를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectAnalysisServiceTest {

    @Mock
    private ProjectAnalysisRepository projectAnalysisRepository;

    @InjectMocks
    private ProjectAnalysisService projectAnalysisService;

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

        projectAnalysisService.completeAnalysis(
                req("a1", "failed", null, null, "late failure"));

        assertThat(a.getAnalysisFlag()).isEqualTo(AnalysisFlag.DONE); // 변하지 않음
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
