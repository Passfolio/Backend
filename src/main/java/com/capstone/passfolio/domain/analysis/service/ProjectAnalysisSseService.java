package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프로젝트 분석 SSE — 개별 repo 완료 + 배치 전체 완료 이벤트를 FE로 푸시.
 * AiSseService 패턴 미러(userId별 emitter, 5분 타임아웃).
 */
@Slf4j
@Service
public class ProjectAnalysisSseService {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final String ANALYSIS_EVENT = "PROJECT_ANALYSIS_STATUS";
    private static final String BATCH_EVENT = "PROJECT_ANALYSIS_BATCH_STATUS";

    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));
        emitters.put(userId, emitter);
        log.info("[ProjectAnalysisSSE] Subscribed. userId={}, active={}", userId, emitters.size());
        return emitter;
    }

    /** 개별 repo 완료 통지(DONE/FAILED). */
    public void pushAnalysis(Long userId, ProjectAnalysisDto.AnalysisSsePayload payload) {
        send(userId, ANALYSIS_EVENT, payload, "analysisId=" + payload.getAnalysisId());
    }

    /** 배치 전체 완료 통지. */
    public void pushBatch(Long userId, ProjectAnalysisDto.BatchSsePayload payload) {
        send(userId, BATCH_EVENT, payload, "batchId=" + payload.getBatchId());
    }

    private void send(Long userId, String event, Object data, String logCtx) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            log.debug("[ProjectAnalysisSSE] No emitter for userId={}. Skipping ({}).", userId, logCtx);
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
            log.info("[ProjectAnalysisSSE] Pushed {}. userId={}, {}", event, userId, logCtx);
        } catch (IOException e) {
            emitters.remove(userId);
            log.warn("[ProjectAnalysisSSE] Push failed (client disconnected). userId={}", userId);
        }
    }
}
