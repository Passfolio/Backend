package com.capstone.passfolio.domain.analysis.service;

/**
 * 배치 분석 전체 완료 시 사용자 SMS 통지(추상화). 실제 전송(AWS SNS 등)·전화번호 수집은
 * 후속 구현 — 현재는 로그 스텁({@link LoggingSmsNotifier})으로 배치 로직을 완성한다.
 */
public interface SmsNotifier {
    void notifyBatchCompleted(Long userId, String batchId, int total, boolean allSuccess);
}
