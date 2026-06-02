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
 * {@link ProjectAnalysisWriter#applyCompletion} 단위 테스트 — 완료 콜백의 DB 변경(상태/매핑/멱등) 검증.
 * (통지·배치 오케스트레이션은 ProjectAnalysisServiceTest가 담당.)
 */
@ExtendWith(MockitoExtension.class)
class ProjectAnalysisWriterTest {

    @Mock private ProjectAnalysisRepository projectAnalysisRepository;
    @InjectMocks private ProjectAnalysisWriter writer;

    private ProjectAnalysis inProgress(String id) {
        return ProjectAnalysis.builder()
                .id(id).batchId("b1").repoUrl("https://github.com/o/r")
                .analysisFlag(AnalysisFlag.IN_PROGRESS).build();
    }

    private ProjectAnalysisDto.WebhookCompleteRequest req(String status, String cdn, String svc, String err) {
        return new ProjectAnalysisDto.WebhookCompleteRequest("a1", status, cdn, svc, err);
    }

    @Test
    @DisplayName("analyzed + cdnUrl → DONE, cdn/service 저장, result.failed=false")
    void apply_success() {
        ProjectAnalysis a = inProgress("a1");
        given(projectAnalysisRepository.findById("a1")).willReturn(Optional.of(a));

        ProjectAnalysisWriter.CompletionResult r = writer.applyCompletion(req("analyzed", "https://cdn/r.json", "Svc", null));

        assertThat(a.getAnalysisFlag()).isEqualTo(AnalysisFlag.DONE);
        assertThat(a.getResultCdnUrl()).isEqualTo("https://cdn/r.json");
        assertThat(a.getServiceName()).isEqualTo("Svc");
        assertThat(r.failed()).isFalse();
        assertThat(r.batchId()).isEqualTo("b1");
        assertThat(r.statusName()).isEqualTo("DONE");
    }

    @Test
    @DisplayName("성공인데 cdnUrl 없음 → FAILED 강제")
    void apply_success_without_cdn_forces_failed() {
        ProjectAnalysis a = inProgress("a1");
        given(projectAnalysisRepository.findById("a1")).willReturn(Optional.of(a));

        ProjectAnalysisWriter.CompletionResult r = writer.applyCompletion(req("analyzed", "  ", null, null));

        assertThat(a.getAnalysisFlag()).isEqualTo(AnalysisFlag.FAILED);
        assertThat(r.failed()).isTrue();
    }

    @Test
    @DisplayName("failed 상태 → FAILED, 사유 저장")
    void apply_failure() {
        ProjectAnalysis a = inProgress("a1");
        given(projectAnalysisRepository.findById("a1")).willReturn(Optional.of(a));

        ProjectAnalysisWriter.CompletionResult r = writer.applyCompletion(req("failed", null, null, "clone error"));

        assertThat(a.getAnalysisFlag()).isEqualTo(AnalysisFlag.FAILED);
        assertThat(a.getFailureReason()).isEqualTo("clone error");
        assertThat(r.failed()).isTrue();
    }

    @Test
    @DisplayName("이미 종료(DONE) → null(멱등), 상태 불변")
    void apply_idempotent_returns_null() {
        ProjectAnalysis a = ProjectAnalysis.builder()
                .id("a1").repoUrl("r").analysisFlag(AnalysisFlag.DONE).build();
        given(projectAnalysisRepository.findById("a1")).willReturn(Optional.of(a));

        ProjectAnalysisWriter.CompletionResult r = writer.applyCompletion(req("failed", null, null, "late"));

        assertThat(r).isNull();
        assertThat(a.getAnalysisFlag()).isEqualTo(AnalysisFlag.DONE);
    }

    @Test
    @DisplayName("미존재 → PROJECT_ANALYSIS_NOT_FOUND")
    void apply_not_found() {
        given(projectAnalysisRepository.findById("a1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> writer.applyCompletion(req("analyzed", "https://cdn/r.json", "S", null)))
                .isInstanceOf(RestException.class)
                .extracting(e -> ((RestException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROJECT_ANALYSIS_NOT_FOUND);
    }
}
