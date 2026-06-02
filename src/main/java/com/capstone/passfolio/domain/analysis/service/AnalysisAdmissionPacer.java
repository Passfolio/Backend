package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 분석 디스패치 admission 페이서 — Redisson 분산 RRateLimiter로 디스패치 rate를 제한한다.
 * Lambda의 Gemini rate 한도 도중 거부(→전체 재분석 낭비)를 사전에 방지하기 위해, Spring이
 * interval당 허용 건수만 SQS로 내보낸다. 초과 시 429(PROJECT_ANALYSIS_RATE_LIMITED) → 재시도.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisAdmissionPacer {

    private static final String LIMITER_KEY = "admission:analysis:dispatch";

    private final RedissonClient redissonClient;

    @Value("${analysis.dispatch.rate-limit}")
    private long rate;

    @Value("${analysis.dispatch.rate-interval-seconds}")
    private long intervalSeconds;

    /** 디스패치 슬롯 1개 확보. 실패(rate 초과) 시 예외. */
    public void acquireOrThrow() {
        acquireOrThrow(1);
    }

    /** 디스패치 슬롯 permits개 확보(배치=all-or-none). 실패(rate 초과) 시 예외. */
    public void acquireOrThrow(int permits) {
        RRateLimiter limiter = redissonClient.getRateLimiter(LIMITER_KEY);
        // 최초 1회만 설정(이미 설정돼 있으면 no-op). interval당 rate건 OVERALL(모든 인스턴스 합산).
        limiter.trySetRate(RateType.OVERALL, rate, intervalSeconds, RateIntervalUnit.SECONDS);
        if (!limiter.tryAcquire(permits)) {
            log.info("[AnalysisAdmissionPacer] dispatch rate exceeded ({}/{}s, need {}) — rejecting",
                    rate, intervalSeconds, permits);
            throw new RestException(ErrorCode.PROJECT_ANALYSIS_RATE_LIMITED);
        }
    }
}
