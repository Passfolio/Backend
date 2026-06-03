package com.capstone.passfolio.domain.analysis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * NONSTOP 포트폴리오 생성용 배치 메타(업로드 PDF URL + 목적)를 transient 보관.
 * batchId 키로 Redis(배치 TTL)에 두고, all-done·전원성공 시 읽어 FastAPI 핸드오프에 사용한다.
 * phone과 달리 민감정보가 아니라 평문 저장. (BatchPhoneStore와 동일 수명/패턴.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchPortfolioStore {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${analysis.dispatch.batch-ttl-hours:6}")
    private long batchTtlHours;

    private String pdfKey(String batchId) { return "analysis:batch:" + batchId + ":pf:pdf"; }
    private String purposeKey(String batchId) { return "analysis:batch:" + batchId + ":pf:purpose"; }

    /** NONSTOP 포폴 메타 저장. pdfUrl/purpose 둘 다 있을 때만(없으면 no-op = STEP 또는 포폴 미요청). */
    public void store(String batchId, String pdfUrl, String purpose) {
        if (pdfUrl == null || pdfUrl.isBlank() || purpose == null || purpose.isBlank()) return;
        Duration ttl = Duration.ofHours(batchTtlHours);
        stringRedisTemplate.opsForValue().set(pdfKey(batchId), pdfUrl.trim(), ttl);
        stringRedisTemplate.opsForValue().set(purposeKey(batchId), purpose.trim().toUpperCase(), ttl);
    }

    /** 업로드 PDF URL(없으면 null = 포폴 미요청). */
    public String readPdfUrl(String batchId) {
        return stringRedisTemplate.opsForValue().get(pdfKey(batchId));
    }

    /** 포폴 목적 EDIT|GENERATE(없으면 null). */
    public String readPurpose(String batchId) {
        return stringRedisTemplate.opsForValue().get(purposeKey(batchId));
    }

    private String jobByBatchKey(String batchId) { return "analysis:batch:" + batchId + ":pf:job"; }
    private String batchByJobKey(Long beJobId) { return "analysis:pf:job:" + beJobId + ":batch"; }

    /** 포폴 AiJob(beJobId)↔batchId 양방향 매핑 — 완료 콜백→batch 식별(SMS), 진행중 페이지→jobId 노출(렌더). */
    public void linkJob(String batchId, Long beJobId) {
        if (batchId == null || beJobId == null) return;
        Duration ttl = Duration.ofHours(batchTtlHours);
        stringRedisTemplate.opsForValue().set(jobByBatchKey(batchId), beJobId.toString(), ttl);
        stringRedisTemplate.opsForValue().set(batchByJobKey(beJobId), batchId, ttl);
    }

    /** batchId의 포폴 AiJob beJobId(없으면 null) — FE 진행중 페이지가 polling할 대상. */
    public Long readJobByBatch(String batchId) {
        String v = stringRedisTemplate.opsForValue().get(jobByBatchKey(batchId));
        return v == null ? null : Long.valueOf(v);
    }

    /** 포폴 AiJob beJobId가 속한 batchId(없으면 null) — completeJob이 NONSTOP 포폴인지 판정. */
    public String readBatchByJob(Long beJobId) {
        return beJobId == null ? null : stringRedisTemplate.opsForValue().get(batchByJobKey(beJobId));
    }

    private String handoffKey(String batchId) { return "analysis:batch:" + batchId + ":pf:handoff"; }

    // 핸드오프 in-flight 플래그: all-done 후 FastAPI 호출(~1-2s) 동안 batch는 전원성공·jobId 미링크라
    // buildBatchStatus가 retryable=true로 오탐한다. 이 플래그가 켜진 동안은 retryable에서 제외해
    // "생성 시작 실패" 오표시를 막는다. 짧은 TTL로 스레드 크래시 시 self-heal(이후 자연스레 retryable 노출).
    private static final Duration HANDOFF_INFLIGHT_TTL = Duration.ofSeconds(120);

    /** 핸드오프 시도 시작 표시(FastAPI 호출 직전). */
    public void markHandoffInProgress(String batchId) {
        if (batchId == null) return;
        stringRedisTemplate.opsForValue().set(handoffKey(batchId), "1", HANDOFF_INFLIGHT_TTL);
    }

    /** 핸드오프 시도 종료 표시(성공/실패 무관, finally). */
    public void clearHandoffInProgress(String batchId) {
        if (batchId == null) return;
        stringRedisTemplate.delete(handoffKey(batchId));
    }

    /** 핸드오프가 in-flight인지(true면 retryable 오탐 방지로 재시도 미노출). */
    public boolean isHandoffInProgress(String batchId) {
        return batchId != null && Boolean.TRUE.equals(stringRedisTemplate.hasKey(handoffKey(batchId)));
    }
}
