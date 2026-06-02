package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 멈춘 분석 안전망 — Lambda가 완료/실패 웹훅을 끝내 보내지 못한 경우(crash/timeout/메시지 유실)
 * IN_PROGRESS로 영구 정체되어 배치가 닫히지 않는 것을 방지한다.
 * stale(마지막 갱신이 cutoff 이전)한 IN_PROGRESS를 찾아 합성 'failed' 콜백으로 종료 처리
 * → 기존 completeAnalysis 경로 재사용(개별 SSE + 배치 카운터 감소 + all-done 판정).
 * (Lambda 최대 실행 15분 → cutoff는 그보다 넉넉히, 배치 Redis TTL 6h 이내.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleAnalysisScheduler {

    private final ProjectAnalysisRepository projectAnalysisRepository;
    private final ProjectAnalysisService projectAnalysisService;

    @Value("${analysis.dispatch.stale-timeout-minutes:30}")
    private long staleTimeoutMinutes;

    @Scheduled(cron = "0 */5 * * * *") // 5분마다
    public void expireStaleAnalyses() {
        MDC.put("scheduler", "stale-analysis");
        try {
            LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(staleTimeoutMinutes);
            List<ProjectAnalysis> stale = projectAnalysisRepository
                    .findByAnalysisFlagAndLastModifiedAtBefore(AnalysisFlag.IN_PROGRESS, cutoff);
            if (stale.isEmpty()) {
                log.debug("[StaleAnalysisScheduler] none stale (cutoff={})", cutoff);
                return;
            }
            log.warn("[StaleAnalysisScheduler] expiring {} stale IN_PROGRESS analyses (cutoff={})", stale.size(), cutoff);
            for (ProjectAnalysis a : stale) {
                try {
                    // 합성 failed 콜백 — completeAnalysis가 멱등·배치 종료 처리까지 수행.
                    projectAnalysisService.completeAnalysis(new ProjectAnalysisDto.WebhookCompleteRequest(
                            a.getId(), "failed", null, null,
                            "stale timeout: no completion within " + staleTimeoutMinutes + "m"));
                } catch (Exception e) {
                    log.error("[StaleAnalysisScheduler] expire failed. analysisId={}", a.getId(), e);
                }
            }
        } finally {
            MDC.remove("scheduler");
        }
    }
}
