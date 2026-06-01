package com.capstone.passfolio.domain.analysis.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.List;

import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StaleAnalysisSchedulerTest {

    @Mock private ProjectAnalysisRepository projectAnalysisRepository;
    @Mock private ProjectAnalysisService projectAnalysisService;

    @InjectMocks
    private StaleAnalysisScheduler scheduler;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "staleTimeoutMinutes", 30L);
    }

    private ProjectAnalysis stale(String id) {
        return ProjectAnalysis.builder().id(id).repoUrl("r").analysisFlag(AnalysisFlag.IN_PROGRESS).build();
    }

    @Test
    void expires_each_stale_via_failed_callback_and_continues_on_error() {
        given(projectAnalysisRepository.findByAnalysisFlagAndLastModifiedAtBefore(eq(AnalysisFlag.IN_PROGRESS), any(LocalDateTime.class)))
                .willReturn(List.of(stale("a1"), stale("a2")));
        // a1 처리 중 예외 → a2는 계속 진행되어야 함
        willThrow(new RuntimeException("redis down"))
                .given(projectAnalysisService).completeAnalysis(org.mockito.ArgumentMatchers.argThat(
                        r -> r != null && "a1".equals(r.getAnalysisId())));

        scheduler.expireStaleAnalyses();

        then(projectAnalysisService).should(times(2)).completeAnalysis(any());
    }
}
