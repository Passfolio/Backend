package com.capstone.passfolio.domain.analysis.entity.enums;

public enum AnalysisFlag {
    YET,          // 분석 전(초기)
    IN_PROGRESS,  // Lambda로 디스패치되어 분석 진행 중
    DONE,         // 분석 완료(결과 CDN URL 보유)
    FAILED        // 분석 실패(failureReason 보유)
}
