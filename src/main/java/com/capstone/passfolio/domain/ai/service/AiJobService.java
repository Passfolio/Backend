package com.capstone.passfolio.domain.ai.service;

import com.capstone.passfolio.domain.ai.client.AiApiClient;
import com.capstone.passfolio.domain.ai.dto.AiDto;
import com.capstone.passfolio.domain.ai.entity.AiJob;
import com.capstone.passfolio.domain.ai.entity.AiJobStatus;
import com.capstone.passfolio.domain.ai.entity.AiJobType;
import com.capstone.passfolio.domain.ai.repository.AiJobRepository;
import com.capstone.passfolio.domain.file.entity.File;
import com.capstone.passfolio.common.util.FileUrlUtils;
import com.capstone.passfolio.domain.file.service.FileService;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiJobService {

    private final FileService fileService;
    private final AiApiClient aiApiClient;
    private final AiJobRepository aiJobRepository;
    private final AiJobWriter aiJobWriter;

    public AiDto.JobInitResponse startPortfolioFromPdf(Long userId, Long fileId) {
        return startJob(userId, fileId, AiJobType.PORTFOLIO_FROM_PDF, null, null);
    }

    public AiDto.JobInitResponse startCoverLetterFromPdf(Long userId, Long fileId) {
        return startJob(userId, fileId, AiJobType.COVER_LETTER_FROM_PDF, null, null);
    }

    public AiDto.JobInitResponse startCoverLetterFromPortfolio(Long userId, Long fileId,
                                                                String jobPosition, String career) {
        return startJob(userId, fileId, AiJobType.COVER_LETTER_FROM_PORTFOLIO, jobPosition, career);
    }

    public AiDto.JobInitResponse startPortfolioFromCoverLetter(Long userId, Long fileId,
                                                                String jobPosition, String career) {
        return startJob(userId, fileId, AiJobType.PORTFOLIO_FROM_COVER_LETTER, jobPosition, career);
    }

    @Transactional
    public void completeJob(AiDto.JobCompleteRequest dto) {
        AiJob job = aiJobRepository.findByAiJobId(dto.getAiJobId())
                .orElseThrow(() -> new RestException(ErrorCode.AI_JOB_NOT_FOUND));

        if (job.getStatus() != AiJobStatus.PENDING) {
            log.info("[AiJobService] completeJob skipped (already {}). aiJobId={}", job.getStatus(), dto.getAiJobId());
            return;
        }

        if ("DONE".equalsIgnoreCase(dto.getStatus())
                && (dto.getOutputPdfUrl() == null || dto.getOutputPdfUrl().isBlank())) {
            log.warn("[AiJobService] DONE with no outputPdfUrl, forcing ERROR. aiJobId={}", dto.getAiJobId());
            job.markError("AI reported DONE but provided no output URL");
            return;
        }

        if ("DONE".equalsIgnoreCase(dto.getStatus())) {
            job.markDone(dto.getOutputPdfUrl());
            log.info("[AiJobService] Job DONE. beJobId={}, aiJobId={}", job.getId(), dto.getAiJobId());
        } else {
            job.markError(dto.getErrorMessage());
            log.info("[AiJobService] Job ERROR. beJobId={}, aiJobId={}", job.getId(), dto.getAiJobId());
        }
    }

    @Transactional(readOnly = true)
    public AiDto.JobStatusResponse getJobStatus(Long userId, Long jobId) {
        AiJob job = aiJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new RestException(ErrorCode.AI_JOB_NOT_FOUND));

        return AiDto.JobStatusResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus().name())
                .outputPdfS3Url(job.getOutputPdfS3Url())
                .errorMessage(job.getErrorMessage())
                .build();
    }

    private AiDto.JobInitResponse startJob(Long userId, Long fileId, AiJobType type,
                                            String jobPosition, String career) {
        File file = fileService.validateFileOwner(fileId, userId);
        String pdfS3Url = FileUrlUtils.buildCdnUrl(file.getS3ObjectKey());

        Long jobId = aiJobWriter.createPendingJob(userId, fileId, type);
        log.info("[AiJobService] PENDING job created. beJobId={}, type={}, userId={}", jobId, type, userId);

        try {
            AiDto.AiJobInitResponse aiResponse = callAiForJobStart(type, pdfS3Url, jobPosition, career, userId);
            aiJobWriter.assignAiJobId(jobId, aiResponse.getJobId());
            log.info("[AiJobService] Job started. beJobId={}, aiJobId={}, type={}, userId={}",
                    jobId, aiResponse.getJobId(), type, userId);
            return AiDto.JobInitResponse.builder().jobId(jobId).build();
        } catch (Exception e) {
            log.error("[AiJobService] AI call failed, marking ERROR. beJobId={}, type={}", jobId, type, e);
            aiJobWriter.markError(jobId, e.getMessage());
            throw e;
        }
    }

    private AiDto.AiJobInitResponse callAiForJobStart(AiJobType type, String pdfS3Url,
                                                        String jobPosition, String career, Long userId) {
        return switch (type) {
            case PORTFOLIO_FROM_PDF -> aiApiClient.requestPortfolioFromPdf(pdfS3Url, userId);
            case COVER_LETTER_FROM_PDF -> aiApiClient.requestCoverLetterFromPdf(pdfS3Url, userId);
            case COVER_LETTER_FROM_PORTFOLIO -> aiApiClient.requestCoverLetterFromPortfolio(
                    AiDto.AiCoverLetterRequest.builder()
                            .pdfS3Url(pdfS3Url)
                            .jobPosition(jobPosition)
                            .career(career)
                            .userId(userId)
                            .build());
            case PORTFOLIO_FROM_COVER_LETTER -> aiApiClient.requestPortfolioFromCoverLetter(
                    AiDto.AiCoverLetterRequest.builder()
                            .pdfS3Url(pdfS3Url)
                            .jobPosition(jobPosition)
                            .career(career)
                            .userId(userId)
                            .build());
        };
    }

}
