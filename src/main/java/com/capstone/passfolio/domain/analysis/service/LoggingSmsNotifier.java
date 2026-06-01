package com.capstone.passfolio.domain.analysis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SMS 스텁 — 실제 전송 대신 로그만 남긴다. 실제 구현(AWS SNS + User 전화번호) 전까지
 * 배치 완료 통지 경로를 완성시킨다. (실 구현 시 이 빈을 교체하면 호출부 불변.)
 */
@Slf4j
@Component
public class LoggingSmsNotifier implements SmsNotifier {

    @Override
    public void notifyBatchCompleted(Long userId, String batchId, int total, boolean allSuccess) {
        log.info("[SMS-STUB] batch completed → would SMS user. userId={}, batchId={}, total={}, allSuccess={}",
                userId, batchId, total, allSuccess);
    }
}
