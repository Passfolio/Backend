package com.capstone.passfolio.domain.ai.service;

import com.capstone.passfolio.domain.ai.client.AiApiClient;
import com.capstone.passfolio.domain.ai.dto.AiDto;
import com.capstone.passfolio.domain.ai.entity.AiJob;
import com.capstone.passfolio.domain.ai.entity.AiJobStatus;
import com.capstone.passfolio.domain.ai.entity.AiJobType;
import com.capstone.passfolio.domain.ai.repository.AiJobRepository;
import com.capstone.passfolio.common.notification.DiscordNotifier;
import com.capstone.passfolio.common.util.FileUrlUtils;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.domain.analysis.service.AnalysisArtifactPresigner;
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
    private final DiscordNotifier discordNotifier; // 관측용: FastAPI 호출/완료 웹훅 가시화(best-effort)
    private final ProjectAnalysisRepository projectAnalysisRepository; // 로드맵 평가 시 analysisIds → CDN URL 해석
    private final AnalysisArtifactPresigner analysisArtifactPresigner; // cdn 분석 URL → presigned S3 URL(봇룰 우회)

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

            // 로드맵 잡은 결과(result_json)가 있는 전용 웹훅(completeRoadmapJob)으로 완료된다.
            // PDF 없는 공용 웹훅이 잘못 도달해도 여기서 DONE-no-output→ERROR로 강등되지 않도록 방어.
            if (job.getType() == AiJobType.ROADMAP_FROM_ANALYSES) {
                log.warn("[AiJobService] completeJob ignored for ROADMAP job — use /roadmap/complete. aiJobId={}", dto.getAiJobId());
                return;
            }

            if (job.getStatus() != AiJobStatus.PENDING) {
                log.info("[AiJobService] completeJob skipped (already {}). aiJobId={}", job.getStatus(), dto.getAiJobId());
                return;
            }

            if ("DONE".equalsIgnoreCase(dto.getStatus())
                    && (dto.getOutputPdfUrl() == null || dto.getOutputPdfUrl().isBlank())) {
                log.warn("[AiJobService] DONE with no outputPdfUrl, forcing ERROR. aiJobId={}", dto.getAiJobId());
                job.markError("AI reported DONE but provided no output URL");
                aiSseService.push(job.getUserId(), job.getId(), job.getStatus().name(), null);
                discordNotifier.send(String.format(
                        "📥 [AI 웹훅] DONE이나 output 없음 → ERROR 처리. aiJobId=%s, beJobId=%d",
                        dto.getAiJobId(), job.getId()));
                return;
            }

            // NONSTOP 포폴 작업이면(배치 매핑 존재) 완료 후 SMS 통지. 일반 /upload 작업이면 null → 통지 없음.
            String pfBatchId = batchPortfolioStore.readBatchByJob(job.getId());
            String origin = pfBatchId != null ? "NONSTOP batch=" + pfBatchId : "manual";
            if ("DONE".equalsIgnoreCase(dto.getStatus())) {
                job.markDone(toOutputCdnUrl(dto.getOutputPdfUrl()));
                log.info("[AiJobService] Job DONE. beJobId={}, aiJobId={}", job.getId(), dto.getAiJobId());
                aiSseService.push(job.getUserId(), job.getId(), job.getStatus().name(), job.getOutputPdfUrl());
                if (pfBatchId != null) smsNotifier.notifyPortfolioCompleted(job.getUserId(), pfBatchId, true);
                discordNotifier.send(String.format("📥 [AI 웹훅] DONE — aiJobId=%s, beJobId=%d, %s, outputPdfUrl=%s",
                        dto.getAiJobId(), job.getId(), origin, job.getOutputPdfUrl()));
            } else {
                job.markError(dto.getErrorMessage());
                log.info("[AiJobService] Job ERROR. beJobId={}, aiJobId={}", job.getId(), dto.getAiJobId());
                aiSseService.push(job.getUserId(), job.getId(), job.getStatus().name(), null);
                if (pfBatchId != null) smsNotifier.notifyPortfolioCompleted(job.getUserId(), pfBatchId, false);
                discordNotifier.send(String.format("📥 [AI 웹훅] ERROR — aiJobId=%s, beJobId=%d, %s, err=%s",
                        dto.getAiJobId(), job.getId(), origin, dto.getErrorMessage()));
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
                .resultJson(job.getResultJson())
                .errorMessage(job.getErrorMessage())
                .build();
    }

    /**
     * 수동 로드맵 평가 작업 시작.
     * analysisIds → DONE 분석의 CDN URL 해석(IDOR 가드) → FastAPI /roadmap/assess 호출.
     * PDF 없이 코드 분석 URL만 사용하므로 fileId = null.
     * BE가 ai_job_id(UUID)를 직접 생성해 FastAPI에 전달 — FastAPI가 완료 시 동일 ID로 콜백.
     */
    public AiDto.JobInitResponse startRoadmapAssess(Long userId, java.util.List<String> analysisIds, boolean merge) {
        java.util.List<String> codeUrls = resolveCodeAnalysisUrls(analysisIds, userId);
        Long jobId = aiJobWriter.createPendingJob(userId, null, AiJobType.ROADMAP_FROM_ANALYSES);
        log.info("[AiJobService] PENDING roadmap job created. beJobId={}, userId={}, urls={}", jobId, userId, codeUrls == null ? 0 : codeUrls.size());

        String aiJobId = java.util.UUID.randomUUID().toString();
        aiJobWriter.assignAiJobId(jobId, aiJobId);

        try {
            aiApiClient.assessRoadmap(AiDto.AiRoadmapRequest.builder()
                    .aiJobId(aiJobId)
                    .codeAnalysisUrls(codeUrls)
                    .merge(merge)
                    .build());
            log.info("[AiJobService] Roadmap assess dispatched. beJobId={}, aiJobId={}", jobId, aiJobId);
            return AiDto.JobInitResponse.builder().jobId(jobId).build();
        } catch (Exception e) {
            log.error("[AiJobService] Roadmap assess dispatch failed. beJobId={}", jobId, e);
            aiJobWriter.markError(jobId, e.getMessage());
            throw e;
        }
    }

    /**
     * 로드맵 평가 완료 콜백 처리 (POST /api/v1/ai/roadmap/complete — AI 서버 전용).
     * PDF 계열의 completeJob과 별도 엔드포인트로 분리 — result가 JSON 배열이므로 outputPdfUrl 아님.
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeRoadmapJob(AiDto.RoadmapCompleteRequest dto) {
        MDC.put("aiJobId", dto.getAiJobId());
        try {
            AiJob job = aiJobRepository.findByAiJobId(dto.getAiJobId())
                    .orElseThrow(() -> new RestException(ErrorCode.AI_JOB_NOT_FOUND));
            MDC.put("beJobId", String.valueOf(job.getId()));

            if (job.getStatus() != AiJobStatus.PENDING) {
                log.info("[AiJobService] completeRoadmapJob skipped (already {}). aiJobId={}", job.getStatus(), dto.getAiJobId());
                return;
            }

            if ("DONE".equalsIgnoreCase(dto.getStatus())) {
                if (dto.getResult() == null || dto.getResult().isNull()) {
                    log.warn("[AiJobService] Roadmap DONE with no result, forcing ERROR. aiJobId={}", dto.getAiJobId());
                    job.markError("Roadmap AI reported DONE but provided no result");
                    aiSseService.push(job.getUserId(), job.getId(), job.getStatus().name(), null);
                    return;
                }
                job.markDoneWithResult(dto.getResult().toString());
                log.info("[AiJobService] Roadmap Job DONE. beJobId={}, aiJobId={}", job.getId(), dto.getAiJobId());
                aiSseService.pushRoadmap(job.getUserId(), job.getId(), job.getStatus().name(), job.getResultJson());
                discordNotifier.send(String.format("📥 [로드맵 웹훅] DONE — aiJobId=%s, beJobId=%d",
                        dto.getAiJobId(), job.getId()));
            } else {
                job.markError(dto.getErrorMessage());
                log.info("[AiJobService] Roadmap Job ERROR. beJobId={}, aiJobId={}", job.getId(), dto.getAiJobId());
                aiSseService.push(job.getUserId(), job.getId(), job.getStatus().name(), null);
                discordNotifier.send(String.format("📥 [로드맵 웹훅] ERROR — aiJobId=%s, beJobId=%d, err=%s",
                        dto.getAiJobId(), job.getId(), dto.getErrorMessage()));
            }
        } finally {
            MDC.remove("aiJobId");
            MDC.remove("beJobId");
        }
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
        int analyses = codeAnalysisUrls == null ? 0 : codeAnalysisUrls.size();
        log.info("[NONSTOP] FastAPI 핸드오프 시작 — userId={}, beJobId={}, type={}, purpose={}, pdfUrl={}, codeAnalysisUrls({})={}",
                userId, jobId, type, purpose, pdfUrl, analyses, codeAnalysisUrls);
        discordNotifier.send(String.format(
                "🚀 [NONSTOP] FastAPI 포폴 호출 — userId=%d, beJobId=%d, type=%s, codeAnalysisUrls=%d, pdfUrl=%s",
                userId, jobId, type, analyses, pdfUrl));
        try {
            AiDto.AiJobInitResponse aiResponse = callAiForJobStart(type, pdfUrl, null, null, userId, codeAnalysisUrls);
            if (aiResponse == null || aiResponse.getJobId() == null) {
                throw new RestException(ErrorCode.AI_SERVER_ERROR, "AI 서버가 유효한 jobId를 반환하지 않았습니다.");
            }
            aiJobWriter.assignAiJobId(jobId, aiResponse.getJobId());
            log.info("[NONSTOP] FastAPI 응답 OK — beJobId={}, aiJobId={}", jobId, aiResponse.getJobId());
            discordNotifier.send(String.format("✅ FastAPI 응답 OK — beJobId=%d, aiJobId=%s",
                    jobId, aiResponse.getJobId()));
            return jobId;
        } catch (Exception e) {
            log.warn("[NONSTOP] FastAPI 핸드오프 실패 — userId={}, beJobId={}, type={}, pdfUrl={}, codeAnalysisUrls={}, err={}",
                    userId, jobId, type, pdfUrl, codeAnalysisUrls, e.toString());
            discordNotifier.send(String.format("❌ FastAPI 호출 실패 — userId=%d, beJobId=%d, type=%s, err=%s",
                    userId, jobId, type, e.getMessage()));
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
            // ROADMAP_FROM_ANALYSES는 startRoadmapAssess에서 직접 호출하므로 여기 도달하지 않음
            case ROADMAP_FROM_ANALYSES -> throw new IllegalStateException(
                    "ROADMAP_FROM_ANALYSES는 startRoadmapAssess를 통해서만 시작됩니다.");
        };
    }

    private java.util.List<String> resolveCodeAnalysisUrls(java.util.List<String> analysisIds, Long userId) {
        if (analysisIds == null || analysisIds.isEmpty()) return null;
        // FastAPI가 cdn.passfolio.dev를 직접 받으면 Cloudflare 봇룰에 403되므로, S3 presigned URL로 변환해 전달.
        java.util.List<String> urls = projectAnalysisRepository
                .findAllByIdInAndUser_Id(analysisIds, userId).stream()
                .filter(a -> a.getAnalysisFlag() == AnalysisFlag.DONE && a.getResultCdnUrl() != null)
                .map(a -> analysisArtifactPresigner.presign(a.getResultCdnUrl()))
                .toList();
        return urls.isEmpty() ? null : urls;
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
