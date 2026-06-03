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
}
