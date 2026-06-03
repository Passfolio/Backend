package com.capstone.passfolio.domain.ai.service;

import com.capstone.passfolio.domain.ai.client.AiApiClient;
import com.capstone.passfolio.domain.ai.dto.AiDto;
import com.capstone.passfolio.domain.ai.entity.AiJob;
import com.capstone.passfolio.domain.ai.entity.AiJobStatus;
import com.capstone.passfolio.domain.ai.entity.AiJobType;
import com.capstone.passfolio.domain.ai.repository.AiJobRepository;
import com.capstone.passfolio.common.util.FileUrlUtils;
import com.capstone.passfolio.domain.file.entity.File;
import com.capstone.passfolio.domain.file.service.FileService;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
    private final AiSseService aiSseService;

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

    @Transactional(rollbackFor = Exception.class)
    public void completeJob(AiDto.JobCompleteRequest dto) {
        MDC.put("aiJobId", dto.getAiJobId());
        try {
            AiJob job = aiJobRepository.findByAiJobId(dto.getAiJobId())
                    .orElseThrow(() -> new RestException(ErrorCode.AI_JOB_NOT_FOUND));
            MDC.put("beJobId", String.valueOf(job.getId()));

            if (job.getStatus() != AiJobStatus.PENDING) {
                log.info("[AiJobService] completeJob skipped (already {}). aiJobId={}", job.getStatus(), dto.getAiJobId());
                return;
            }

            if ("DONE".equalsIgnoreCase(dto.getStatus())
                    && (dto.getOutputPdfUrl() == null || dto.getOutputPdfUrl().isBlank())) {
                log.warn("[AiJobService] DONE with no outputPdfUrl, forcing ERROR. aiJobId={}", dto.getAiJobId());
                job.markError("AI reported DONE but provided no output URL");
                aiSseService.push(job.getUserId(), job.getId(), job.getStatus().name(), null);
                return;
            }

            if ("DONE".equalsIgnoreCase(dto.getStatus())) {
                job.markDone(toOutputCdnUrl(dto.getOutputPdfUrl()));
                log.info("[AiJobService] Job DONE. beJobId={}, aiJobId={}", job.getId(), dto.getAiJobId());
                aiSseService.push(job.getUserId(), job.getId(), job.getStatus().name(), job.getOutputPdfUrl());
            } else {
                job.markError(dto.getErrorMessage());
                log.info("[AiJobService] Job ERROR. beJobId={}, aiJobId={}", job.getId(), dto.getAiJobId());
                aiSseService.push(job.getUserId(), job.getId(), job.getStatus().name(), null);
            }
        } finally {
            MDC.remove("aiJobId");
            MDC.remove("beJobId");
        }
    }

    @Transactional(readOnly = true)
    public AiDto.JobStatusResponse getJobStatus(Long userId, Long jobId) {
        AiJob job = aiJobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new RestException(ErrorCode.AI_JOB_NOT_FOUND));

        return AiDto.JobStatusResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus().name())
                .outputPdfUrl(job.getOutputPdfUrl())
                .errorMessage(job.getErrorMessage())
                .build();
    }

    private AiDto.JobInitResponse startJob(Long userId, Long fileId, AiJobType type,
                                            String jobPosition, String career) {
        MDC.put("userId", String.valueOf(userId));
        try {
            File file = fileService.validateFileOwner(fileId, userId);
            String pdfUrl = FileUrlUtils.buildCdnUrl(file.getS3ObjectKey());

            Long jobId = aiJobWriter.createPendingJob(userId, fileId, type);
            log.info("[AiJobService] PENDING job created. beJobId={}, type={}, userId={}", jobId, type, userId);

            try {
                AiDto.AiJobInitResponse aiResponse = callAiForJobStart(type, pdfUrl, jobPosition, career, userId);
                if (aiResponse == null || aiResponse.getJobId() == null) {
                    throw new RestException(ErrorCode.AI_SERVER_ERROR, "AI 서버가 유효한 jobId를 반환하지 않았습니다.");
                }
                aiJobWriter.assignAiJobId(jobId, aiResponse.getJobId());
                log.info("[AiJobService] Job started. beJobId={}, aiJobId={}, type={}, userId={}",
                        jobId, aiResponse.getJobId(), type, userId);
                return AiDto.JobInitResponse.builder().jobId(jobId).build();
            } catch (Exception e) {
                log.error("[AiJobService] AI call failed, marking ERROR. beJobId={}, type={}", jobId, type, e);
                aiJobWriter.markError(jobId, e.getMessage());
                throw e;
            }
        } finally {
            MDC.remove("userId");
        }
    }

    private AiDto.AiJobInitResponse callAiForJobStart(AiJobType type, String pdfUrl,
                                                        String jobPosition, String career, Long userId) {
        return switch (type) {
            case PORTFOLIO_FROM_PDF -> aiApiClient.upgradePortfolio(pdfUrl, userId);
            case COVER_LETTER_FROM_PDF -> aiApiClient.upgradeCoverLetter(pdfUrl, userId);
            case COVER_LETTER_FROM_PORTFOLIO -> aiApiClient.generateCoverLetterFromPortfolio(
                    AiDto.AiCoverLetterRequest.builder()
                            .pdfUrl(pdfUrl)
                            .jobPosition(jobPosition)
                            .career(career)
                            .userId(userId)
                            .build());
            case PORTFOLIO_FROM_COVER_LETTER -> aiApiClient.generatePortfolioFromCoverLetter(
                    AiDto.AiCoverLetterRequest.builder()
                            .pdfUrl(pdfUrl)
                            .jobPosition(jobPosition)
                            .career(career)
                            .userId(userId)
                            .build());
        };
    }

    private String toOutputCdnUrl(String s3Url) {
        if (s3Url == null || s3Url.isBlank()) return s3Url;
        try {
            String path = java.net.URI.create(s3Url).getPath();
            String key = path.startsWith("/") ? path.substring(1) : path;
            return FileUrlUtils.buildCdnUrl(key);
        } catch (Exception e) {
            log.warn("[AiJobService] output_pdf_url CDN 변환 실패, 원본 사용. url={}", s3Url);
            return s3Url;
        }
    }

}
