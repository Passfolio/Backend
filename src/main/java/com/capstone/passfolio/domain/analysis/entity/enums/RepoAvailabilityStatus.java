package com.capstone.passfolio.domain.analysis.entity.enums;

/**
 * repo 사전 분석 가능성 점검 상태. 분석 실행 상태({@link AnalysisFlag})와 별개로,
 * (user, repo)당 1행으로 관리한다(repo_availability 테이블).
 */
public enum RepoAvailabilityStatus {
    CHECKING,    // 점검 디스패치됨(Lambda clone+측정 중)
    AVAILABLE,   // 분석 가능(작업트리·히스토리 한도 이내)
    DISABLED     // 분석 불가(크기 초과 / 접근 불가 등 — reason 보유)
}
