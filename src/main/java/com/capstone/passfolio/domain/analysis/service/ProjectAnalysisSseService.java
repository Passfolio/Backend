package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.domain.analysis.dto.RepoAvailabilityDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프로젝트 분석 SSE — 개별 repo 완료 + 배치 전체 완료 이벤트를 FE로 푸시.
 * 사용자당 <b>다중 연결(멀티탭/멀티기기)</b>을 지원: userId → emitter 집합으로 fan-out한다.
 * (AiSseService의 '사용자당 1 emitter' 한계 해소 — 새 탭이 기존 연결을 끊지 않음.)
 * add/remove는 ConcurrentHashMap.compute로 원자 처리해 orphan emitter를 방지한다.
 */
@Slf4j
@Service
public class ProjectAnalysisSseService {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final String ANALYSIS_EVENT = "PROJECT_ANALYSIS_STATUS";
    private static final String BATCH_EVENT = "PROJECT_ANALYSIS_BATCH_STATUS";
    private static final String PRECHECK_EVENT = "PROJECT_ANALYSIS_PRECHECK_STATUS";

    private final ConcurrentHashMap<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // add를 compute 내부에서 수행 → 동시 remove와의 orphan 레이스 차단.
        emitters.compute(userId, (k, set) -> {
            if (set == null) set = ConcurrentHashMap.newKeySet();
            set.add(emitter);
            return set;
        });
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        log.info("[ProjectAnalysisSSE] Subscribed. userId={}, conns={}", userId, count(userId));
        return emitter;
    }

    private void remove(Long userId, SseEmitter emitter) {
        emitters.compute(userId, (k, set) -> {
            if (set == null) return null;
            set.remove(emitter);
            return set.isEmpty() ? null : set; // 빈 집합은 키 제거(누수 방지)
        });
    }

    private int count(Long userId) {
        Set<SseEmitter> set = emitters.get(userId);
        return set == null ? 0 : set.size();
    }

    /** 개별 repo 완료 통지(DONE/FAILED). */
    public void pushAnalysis(Long userId, ProjectAnalysisDto.AnalysisSsePayload payload) {
        fanout(userId, ANALYSIS_EVENT, payload, "analysisId=" + payload.getAnalysisId());
    }

    /** 배치 전체 완료 통지. */
    public void pushBatch(Long userId, ProjectAnalysisDto.BatchSsePayload payload) {
        fanout(userId, BATCH_EVENT, payload, "batchId=" + payload.getBatchId());
    }

    /** repo 사전 점검 결과 통지(AVAILABLE/DISABLED). */
    public void pushPrecheck(Long userId, RepoAvailabilityDto.PrecheckSsePayload payload) {
        fanout(userId, PRECHECK_EVENT, payload, "repoUrl=" + payload.getRepoUrl());
    }

    private void fanout(Long userId, String event, Object data, String logCtx) {
        Set<SseEmitter> set = (userId == null) ? null : emitters.get(userId);
        if (set == null || set.isEmpty()) {
            log.debug("[ProjectAnalysisSSE] No emitter for userId={}. Skipping ({}).", userId, logCtx);
            return;
        }
        int sent = 0;
        for (SseEmitter emitter : set) { // newKeySet: 동시 변경에 안전한 약한 일관성 순회
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
                sent++;
            } catch (Exception e) { // IOException(연결 끊김)/IllegalState(완료) → 해당 emitter 정리
                remove(userId, emitter);
            }
        }
        log.info("[ProjectAnalysisSSE] Pushed {} to {} conn(s). userId={}, {}", event, sent, userId, logCtx);
    }
}
