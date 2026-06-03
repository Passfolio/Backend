package com.capstone.passfolio.domain.analysis.repository;

import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectAnalysisRepository extends JpaRepository<ProjectAnalysis, String> {

    // 사용자 정보에 맞는 repo url로 분석 조회(디스패치/상태 갱신 경로).
    Optional<ProjectAnalysis> findByUser_IdAndRepoUrl(Long userId, String repoUrl);

    // 배치(그룹) 단위 조회 — all-done 시 결과 수집(FastAPI 핸드오프).
    List<ProjectAnalysis> findByBatchId(String batchId);

    // 사용자 소유 배치 조회 — 사용자 진행중 페이지(타인 batch 접근 차단).
    List<ProjectAnalysis> findByBatchIdAndUser_Id(String batchId, Long userId);

    // 사용자 소유 단건 조회 — 리포트 페이지(타인 분석 접근 차단).
    Optional<ProjectAnalysis> findByIdAndUser_Id(String id, Long userId);

    // 사용자 분석 이력(최근순) — 프로필 '분석 이력' 탭. Pageable로 최근 N건 제한.
    List<ProjectAnalysis> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // stale 안전망 — 지정 시각 이전에 마지막 갱신된 채 특정 상태(IN_PROGRESS)로 멈춘 분석.
    List<ProjectAnalysis> findByAnalysisFlagAndLastModifiedAtBefore(AnalysisFlag flag, LocalDateTime cutoff);

    // 로드맵 평가 시작 시 analysisIds → CDN URL 벌크 해석 (IDOR 가드: 타인 소유 자동 제외).
    List<ProjectAnalysis> findAllByIdInAndUser_Id(Collection<String> ids, Long userId);
}
