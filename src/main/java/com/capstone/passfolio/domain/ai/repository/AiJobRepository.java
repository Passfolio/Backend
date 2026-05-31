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
}
