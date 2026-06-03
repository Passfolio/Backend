package com.capstone.passfolio.domain.ai.service;

import com.capstone.passfolio.domain.ai.entity.AiJob;
import com.capstone.passfolio.domain.ai.entity.AiJobStatus;
import com.capstone.passfolio.domain.ai.entity.AiJobType;
import com.capstone.passfolio.domain.ai.repository.AiJobRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiJobWriter {

    private final AiJobRepository aiJobRepository;

    @Transactional
    public Long createPendingJob(Long userId, Long fileId, AiJobType type) {
        // 중복 PENDING 방지는 "입력 파일 단위" dedup이다. fileId가 null인 NONSTOP 핸드오프는
        // existsBy의 null 인자가 input_file_id IS NULL로 변환돼 (userId,NULL,type,PENDING) 한 슬롯에
        // 모든 NONSTOP이 충돌 → stuck PENDING 하나가 이후 전 배치의 핸드오프를 AI_JOB_ALREADY_PENDING으로
        // 막아버린다. NONSTOP 멱등성은 배치 단위(BatchPortfolioStore.linkJob/readJobByBatch)로 이미 보장되므로
        // 파일 단위 중복검사는 fileId가 있을 때만 적용한다.
        if (fileId != null && aiJobRepository.existsByUserIdAndInputFileIdAndTypeAndStatus(
                userId, fileId, type, AiJobStatus.PENDING)) {
            throw new RestException(ErrorCode.AI_JOB_ALREADY_PENDING);
        }
        AiJob job = AiJob.builder()
                .userId(userId)
                .type(type)
                .status(AiJobStatus.PENDING)
                .inputFileId(fileId)
                .build();
        Long jobId = aiJobRepository.save(job).getId();
        log.info("[AiJob] PENDING 생성 — beJobId={}, userId={}, type={}, inputFileId={}{}",
                jobId, userId, type, fileId, fileId == null ? " (NONSTOP, dedup 미적용)" : "");
        return jobId;
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
