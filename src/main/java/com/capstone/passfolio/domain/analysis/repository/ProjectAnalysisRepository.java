package com.capstone.passfolio.domain.analysis.repository;

import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectAnalysisRepository extends JpaRepository<ProjectAnalysis, String> {

    // 사용자 정보에 맞는 repo url로 분석 조회(디스패치/상태 갱신 경로).
    Optional<ProjectAnalysis> findByUser_IdAndRepoUrl(Long userId, String repoUrl);
}
