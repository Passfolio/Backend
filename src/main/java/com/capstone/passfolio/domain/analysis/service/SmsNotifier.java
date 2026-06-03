package com.capstone.passfolio.domain.analysis.service;

/**
 * 배치 분석 전체 완료 시 사용자 SMS 통지(추상화). 실제 전송은 CoolSMS(솔라피)
 * 구현({@link CoolSmsNotifier})이 담당하고, 전화번호는 {@link BatchPhoneStore}에서 transient 조회한다.
 */
public interface SmsNotifier {
    void notifyBatchCompleted(Long userId, String batchId, int total, boolean allSuccess);

    /** NONSTOP 포트폴리오 생성 완료/실패 통지(phone은 batchId로 transient 조회). */
    void notifyPortfolioCompleted(Long userId, String batchId, boolean success);

    /** NONSTOP 전원성공이나 FastAPI 핸드오프 호출 자체가 실패(분석은 완료, 포폴 생성 미시작·재시도 가능) 통지. */
    void notifyPortfolioHandoffFailed(Long userId, String batchId);
}
