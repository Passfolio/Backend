package com.capstone.passfolio.domain.analysis.service;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 배치(다중 repo 요청) 완료 추적 — Redisson 원자 카운터.
 * remaining(남은 종료 대기 수)·failures(실패 수)만 Redis에 두고, userId/mode/결과는 DB에서 얻는다.
 * recordTerminal의 decrementAndGet 원자성으로 "마지막 1건"만 all-done을 트리거 → FastAPI 중복호출 방지.
 */
@Component
@RequiredArgsConstructor
public class BatchProgressTracker {

    private final RedissonClient redissonClient;

    @Value("${analysis.dispatch.batch-ttl-hours:6}")
    private long batchTtlHours;

    private String remainingKey(String batchId) { return "analysis:batch:" + batchId + ":remaining"; }
    private String failuresKey(String batchId) { return "analysis:batch:" + batchId + ":failures"; }

    /** 배치 등록: 남은 수=total, 실패 수=0 (TTL 부여). */
    public void createBatch(String batchId, int total) {
        Duration ttl = Duration.ofHours(batchTtlHours);
        RAtomicLong remaining = redissonClient.getAtomicLong(remainingKey(batchId));
        remaining.set(total);
        remaining.expire(ttl);
        RAtomicLong failures = redissonClient.getAtomicLong(failuresKey(batchId));
        failures.set(0);
        failures.expire(ttl);
    }

    /**
     * repo 한 건 종료 기록(최초 종료 전이에서만 호출 — 멱등 skip 시 호출 금지).
     * 마지막 1건이면 allDone=true, 실패 0건이면 allSuccess=true.
     */
    public BatchOutcome recordTerminal(String batchId, boolean failed) {
        if (failed) {
            redissonClient.getAtomicLong(failuresKey(batchId)).incrementAndGet();
        }
        long remaining = redissonClient.getAtomicLong(remainingKey(batchId)).decrementAndGet();
        // 정확히 0일 때만 all-done(중복/지연 호출로 음수가 되면 재트리거하지 않음 — 가드).
        if (remaining != 0) {
            return new BatchOutcome(false, false, 0);
        }
        long failures = redissonClient.getAtomicLong(failuresKey(batchId)).get();
        return new BatchOutcome(true, failures == 0, (int) failures);
    }

    /** 배치 종료 결과. allDone일 때만 allSuccess/failures 의미 있음. */
    public record BatchOutcome(boolean allDone, boolean allSuccess, int failures) { }
}
