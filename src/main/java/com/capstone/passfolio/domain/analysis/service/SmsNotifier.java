package com.capstone.passfolio.domain.analysis.service;

/**
 * 배치 분석 전체 완료 시 사용자 SMS 통지(추상화). 실제 전송은 CoolSMS(솔라피)
 * 구현({@link CoolSmsNotifier})이 담당하고, 전화번호는 {@link BatchPhoneStore}에서 transient 조회한다.
 */
public interface SmsNotifier {
    void notifyBatchCompleted(Long userId, String batchId, int total, boolean allSuccess);
}
