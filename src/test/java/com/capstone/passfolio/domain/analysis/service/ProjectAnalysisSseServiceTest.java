package com.capstone.passfolio.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import java.util.Set;

import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@link ProjectAnalysisSseService} 단위 테스트 — 사용자당 다중 연결 등록/격리 검증.
 * (실제 SSE 전송은 통합 테스트에서.)
 */
class ProjectAnalysisSseServiceTest {

    @SuppressWarnings("unchecked")
    private Map<Long, Set<SseEmitter>> emittersOf(ProjectAnalysisSseService svc) {
        return (Map<Long, Set<SseEmitter>>) ReflectionTestUtils.getField(svc, "emitters");
    }

    @Test
    @DisplayName("같은 사용자 2개 구독 → 2 연결 공존(기존 연결 안 끊김)")
    void multiple_connections_per_user() {
        ProjectAnalysisSseService svc = new ProjectAnalysisSseService();
        svc.subscribe(7L);
        svc.subscribe(7L);
        svc.subscribe(9L);

        assertThat(emittersOf(svc).get(7L)).hasSize(2);
        assertThat(emittersOf(svc).get(9L)).hasSize(1);
    }

    @Test
    @DisplayName("emitter 없는 사용자에게 push → 예외 없이 skip")
    void push_without_emitter_is_safe() {
        ProjectAnalysisSseService svc = new ProjectAnalysisSseService();
        assertThatCode(() -> svc.pushAnalysis(123L,
                ProjectAnalysisDto.AnalysisSsePayload.builder().analysisId("a1").status("DONE").build()))
                .doesNotThrowAnyException();
        assertThatCode(() -> svc.pushBatch(null,
                ProjectAnalysisDto.BatchSsePayload.builder().batchId("b1").status("ALL_DONE").build()))
                .doesNotThrowAnyException();
    }
}
