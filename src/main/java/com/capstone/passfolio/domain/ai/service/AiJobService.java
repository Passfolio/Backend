package com.capstone.passfolio.domain.ai.service;

import com.capstone.passfolio.domain.ai.client.AiApiClient;
import com.capstone.passfolio.domain.ai.dto.AiDto;
import com.capstone.passfolio.domain.ai.entity.AiJob;
import com.capstone.passfolio.domain.ai.entity.AiJobStatus;
import com.capstone.passfolio.domain.ai.entity.AiJobType;
import com.capstone.passfolio.domain.ai.repository.AiJobRepository;
import com.capstone.passfolio.common.util.FileUrlUtils;
import com.capstone.passfolio.domain.analysis.service.BatchPortfolioStore;
import com.capstone.passfolio.domain.analysis.service.SmsNotifier;
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
    private final BatchPortfolioStore batchPortfolioStore; // NONSTOP 포폴 완료 시 batch 식별(SMS)
    private final SmsNotifier smsNotifier;

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

            // NONSTOP 포폴 작업이면(배치 매핑 존재) 완료 후 SMS 통지. 일반 /upload 작업이면 null → 통지 없음.
            String pfBatchId = batchPortfolioStore.readBatchByJob(job.getId());
            if ("DONE".equalsIgnoreCase(dto.getStatus())) {
                job.markDone(toOutputCdnUrl(dto.getOutputPdfUrl()));
                log.info("[AiJobService] Job DONE. beJobId={}, aiJobId={}", job.getId(), dto.getAiJobId());
                aiSseService.push(job.getUserId(), job.getId(), job.getStatus().name(), job.getOutputPdfUrl());
                if (pfBatchId != null) smsNotifier.notifyPortfolioCompleted(job.getUserId(), pfBatchId, true);
            } else {
                job.markError(dto.getErrorMessage());
                log.info("[AiJobService] Job ERROR. beJobId={}, aiJobId={}", job.getId(), dto.getAiJobId());
                aiSseService.push(job.getUserId(), job.getId(), job.getStatus().name(), null);
                if (pfBatchId != null) smsNotifier.notifyPortfolioCompleted(job.getUserId(), pfBatchId, false);
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
                AiDto.AiJobInitResponse aiResponse = callAiForJobStart(type, pdfUrl, jobPosition, career, userId, null);
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

    /**
     * NONSTOP 배치 all-done·전원성공 시 포트폴리오 생성 작업 시작.
     * purpose EDIT(기존 포폴 개선 = PORTFOLIO_FROM_PDF) / GENERATE(자소서로 생성 = PORTFOLIO_FROM_COVER_LETTER).
     * pdfUrl(업로드 PDF CDN URL) + codeAnalysisUrls(분석 final.json URL들)로 FastAPI 호출 → AiJob 등록.
     * fileId 없이(파일 식별은 batch가 보유) AiJob 생성 — 완료 콜백/SSE는 기존 completeJob 경로 재사용.
     */
    public Long startPortfolioFromAnalyses(Long userId, String pdfUrl, String purpose,
                                           java.util.List<String> codeAnalysisUrls) {
        AiJobType type = "GENERATE".equalsIgnoreCase(purpose)
                ? AiJobType.PORTFOLIO_FROM_COVER_LETTER
                : AiJobType.PORTFOLIO_FROM_PDF;
        Long jobId = aiJobWriter.createPendingJob(userId, null, type);
        try {
            AiDto.AiJobInitResponse aiResponse = callAiForJobStart(type, pdfUrl, null, null, userId, codeAnalysisUrls);
            if (aiResponse == null || aiResponse.getJobId() == null) {
                throw new RestException(ErrorCode.AI_SERVER_ERROR, "AI 서버가 유효한 jobId를 반환하지 않았습니다.");
            }
            aiJobWriter.assignAiJobId(jobId, aiResponse.getJobId());
            return jobId;
        } catch (Exception e) {
            aiJobWriter.markError(jobId, e.getMessage());
            throw e;
        }
    }

    private AiDto.AiJobInitResponse callAiForJobStart(AiJobType type, String pdfUrl,
                                                        String jobPosition, String career, Long userId,
                                                        java.util.List<String> codeAnalysisUrls) {
        return switch (type) {
            case PORTFOLIO_FROM_PDF -> aiApiClient.upgradePortfolio(pdfUrl, codeAnalysisUrls, userId);
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
                            .codeAnalysisUrls(codeAnalysisUrls)
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
