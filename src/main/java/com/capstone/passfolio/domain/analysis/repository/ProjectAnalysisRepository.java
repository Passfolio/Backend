package com.capstone.passfolio.domain.analysis.repository;

import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProjectAnalysisRepository extends JpaRepository<ProjectAnalysis, String> {

    // 사용자 정보에 맞는 repo url로 분석 조회(디스패치/상태 갱신 경로).
    Optional<ProjectAnalysis> findByUser_IdAndRepoUrl(Long userId, String repoUrl);

    // 배치(그룹) 단위 조회 — all-done 시 결과 수집(FastAPI 핸드오프).
    List<ProjectAnalysis> findByBatchId(String batchId);

    // stale 안전망 — 지정 시각 이전에 마지막 갱신된 채 특정 상태(IN_PROGRESS)로 멈춘 분석.
    List<ProjectAnalysis> findByAnalysisFlagAndLastModifiedAtBefore(AnalysisFlag flag, LocalDateTime cutoff);
}
