package com.capstone.passfolio.domain.analysis.repository;

import com.capstone.passfolio.domain.analysis.entity.RepoAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RepoAvailabilityRepository extends JpaRepository<RepoAvailability, String> {

    // (user, repo)당 1행(UNIQUE) — 사전점검 upsert / 분석 게이트 조회.
    Optional<RepoAvailability> findByUser_IdAndRepoUrl(Long userId, String repoUrl);

    // FE 리스트/필터용 — 사용자의 전체 점검 상태.
    List<RepoAvailability> findByUser_Id(Long userId);

    // 회원탈퇴 — 사용자 사전점검 행 일괄 삭제(없으면 no-op).
    @Modifying
    @Query("DELETE FROM RepoAvailability r WHERE r.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
