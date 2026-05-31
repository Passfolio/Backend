package com.capstone.passfolio.domain.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiJobScheduler {

    private final AiJobWriter aiJobWriter;

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupStalePendingJobs() {
        MDC.put("scheduler", "stale-job-cleanup");
        try {
            LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusHours(24);
            int count = aiJobWriter.cleanupStalePendingJobs(cutoff);
            if (count > 0) {
                log.info("[Scheduler] Cleaned up {} stale PENDING jobs older than 24h. cutoff={}",
                        count, cutoff);
            } else {
                log.debug("[Scheduler] No stale PENDING jobs found.");
            }
        } finally {
            MDC.clear();
        }
    }
}
