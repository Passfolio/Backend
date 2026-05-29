package com.capstone.passfolio.domain.ai.service;

import com.capstone.passfolio.domain.ai.entity.AiJob;
import com.capstone.passfolio.domain.ai.entity.AiJobStatus;
import com.capstone.passfolio.domain.ai.entity.AiJobType;
import com.capstone.passfolio.domain.ai.repository.AiJobRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AiJobWriter {

    private final AiJobRepository aiJobRepository;

    @Transactional
    public Long createPendingJob(Long userId, Long fileId, AiJobType type) {
        if (aiJobRepository.existsByUserIdAndInputFileIdAndTypeAndStatus(
                userId, fileId, type, AiJobStatus.PENDING)) {
            throw new RestException(ErrorCode.AI_JOB_ALREADY_PENDING);
        }
        AiJob job = AiJob.builder()
                .userId(userId)
                .type(type)
                .status(AiJobStatus.PENDING)
                .inputFileId(fileId)
                .build();
        return aiJobRepository.save(job).getId();
    }

    @Transactional
    public void assignAiJobId(Long jobId, String aiJobId) {
        AiJob job = aiJobRepository.findById(jobId)
                .orElseThrow(() -> new RestException(ErrorCode.AI_JOB_NOT_FOUND));
        job.assignAiJobId(aiJobId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markError(Long jobId, String message) {
        aiJobRepository.findById(jobId)
                .ifPresent(job -> job.markError(message));
    }

    @Transactional
    public int cleanupStalePendingJobs(LocalDateTime cutoff) {
        return aiJobRepository.markStalePendingAsError(
                AiJobStatus.PENDING,
                AiJobStatus.ERROR,
                cutoff,
                "Stale job — automatically expired by scheduler");
    }
}
