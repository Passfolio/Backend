package com.capstone.passfolio.domain.ai.repository;

import com.capstone.passfolio.domain.ai.entity.AiJob;
import com.capstone.passfolio.domain.ai.entity.AiJobStatus;
import com.capstone.passfolio.domain.ai.entity.AiJobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    Optional<AiJob> findByIdAndUserId(Long id, Long userId);

    Optional<AiJob> findByAiJobId(String aiJobId);

    boolean existsByUserIdAndInputFileIdAndTypeAndStatus(
            Long userId, Long inputFileId, AiJobType type, AiJobStatus status);

    @Modifying
    @Query("UPDATE AiJob a SET a.status = :errorStatus, a.errorMessage = :message " +
           "WHERE a.status = :pendingStatus AND a.createdAt < :cutoff")
    int markStalePendingAsError(
            @Param("pendingStatus") AiJobStatus pendingStatus,
            @Param("errorStatus")   AiJobStatus errorStatus,
            @Param("cutoff")        LocalDateTime cutoff,
            @Param("message")       String message);

    // 회원탈퇴 — 사용자 AiJob 일괄 삭제. (ai_jobs는 user FK ON DELETE CASCADE이나 USER soft-delete 시
    // user 행이 남아 cascade가 안 타므로 명시 삭제가 필요하다. 없으면 no-op.)
    @Modifying
    @Query("DELETE FROM AiJob a WHERE a.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
