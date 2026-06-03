package com.capstone.passfolio.domain.ai.service;

import com.capstone.passfolio.domain.ai.dto.AiDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AiSseService {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final String JOB_EVENT_NAME = "AI_JOB_STATUS";

    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onCompletion(() -> {
            emitters.remove(userId);
            log.debug("[SSE] Emitter completed. userId={}", userId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(userId);
            log.debug("[SSE] Emitter timed out. userId={}", userId);
        });
        emitter.onError(e -> {
            emitters.remove(userId);
            log.debug("[SSE] Emitter error. userId={}", userId);
        });

        emitters.put(userId, emitter);
        log.info("[SSE] Subscribed. userId={}, active={}", userId, emitters.size());
        return emitter;
    }

    public void push(Long userId, Long jobId, String status, String outputPdfUrl) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            log.debug("[SSE] No emitter for userId={}. Skipping push.", userId);
            return;
        }

        AiDto.AiJobSsePayload payload = AiDto.AiJobSsePayload.builder()
                .jobId(jobId)
                .status(status)
                .outputPdfUrl(outputPdfUrl)
                .build();

        try {
            emitter.send(SseEmitter.event()
                    .name(JOB_EVENT_NAME)
                    .data(payload));
            log.info("[SSE] Pushed. userId={}, jobId={}, status={}", userId, jobId, status);
        } catch (IOException e) {
            emitters.remove(userId);
            log.warn("[SSE] Push failed (client disconnected). userId={}", userId);
        }
    }

    public void pushRoadmap(Long userId, Long jobId, String status, String resultJson) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            log.debug("[SSE] No emitter for userId={}. Skipping roadmap push.", userId);
            return;
        }

        AiDto.AiJobSsePayload payload = AiDto.AiJobSsePayload.builder()
                .jobId(jobId)
                .status(status)
                .resultJson(resultJson)
                .build();

        try {
            emitter.send(SseEmitter.event()
                    .name(JOB_EVENT_NAME)
                    .data(payload));
            log.info("[SSE] Roadmap pushed. userId={}, jobId={}, status={}", userId, jobId, status);
        } catch (IOException e) {
            emitters.remove(userId);
            log.warn("[SSE] Roadmap push failed (client disconnected). userId={}", userId);
        }
    }
}
