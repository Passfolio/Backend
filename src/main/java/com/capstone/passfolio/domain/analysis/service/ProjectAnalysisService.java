package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.domain.aws.sqs.SqsMessageSender;
import com.capstone.passfolio.domain.github.client.GitHubApiClient;
import com.capstone.passfolio.domain.github.dto.GitHubDto;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectAnalysisService {

    private final ProjectAnalysisRepository projectAnalysisRepository;
    private final ProjectAnalysisWriter projectAnalysisWriter;
    private final UserRepository userRepository;
    private final GitHubApiClient gitHubApiClient;
    private final AnalysisTokenPreparer tokenPreparer;
    private final AnalysisAdmissionPacer admissionPacer;
    private final SqsMessageSender sqsMessageSender;

    @Value("${aws.sqs.analysis-queue-url}")
    private String analysisQueueUrl;

    @Value("${analysis.dispatch.max-repo-size-kb}")
    private long maxRepoSizeKb;

    // Lambda가 보내는 완료 상태 토큰(대소문자 무시) → 성공으로 간주.
    private static final Set<String> SUCCESS_TOKENS = Set.of("analyzed", "done", "success");

    // ============================================================
    // 디스패치 (FE → BE → Lambda)
    // ============================================================

    /**
     * 분석 시작: 토큰 준비(복호·TTL) → repo size 게이트 → admission → TTL 재점검 → KMS 재암호화
     * → ProjectAnalysis 생성 → SQS enqueue → IN_PROGRESS. 외부호출은 트랜잭션 밖(짧은 저장만 txn).
     */
    public ProjectAnalysisDto.StartResponse initiateAnalysis(Long userId, ProjectAnalysisDto.StartRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RestException(ErrorCode.AUTH_USER_NOT_FOUND));
        String githubUsername = user.getGithubLogin();
        String repoUrl = request.getRepoUrl();
        OwnerRepo ownerRepo = parseOwnerRepo(repoUrl);
        String mode = (request.getMode() == null || request.getMode().isBlank()) ? "NONSTOP" : request.getMode();

        // 1) 토큰: Redis 조회 → AESGCM 복호 (+ TTL 점검)
        String plainToken = tokenPreparer.resolvePlaintextWithTtlCheck(userId);

        // 2) repo size 게이트(GitHub API size = KB, history 포함). 정밀 100MB 작업트리는 Lambda clone에서.
        GitHubDto.ApiRepo repo = gitHubApiClient.fetchRepo(plainToken, ownerRepo.owner(), ownerRepo.repo());
        long sizeKb = repo.getSize() != null ? repo.getSize() : 0L;
        if (sizeKb > maxRepoSizeKb) {
            throw new RestException(ErrorCode.ANALYSIS_REPO_SIZE_EXCEEDED);
        }

        // 3) admission(디스패치 rate 페이싱) — 초과 시 429
        admissionPacer.acquireOrThrow();

        // 4) 전송 직전 TTL 재점검 + KMS 재암호화
        tokenPreparer.assertSufficientTtl(userId);
        String encryptedToken = tokenPreparer.reencryptForLambda(plainToken);

        // 5) ProjectAnalysis(YET) 생성·저장
        String analysisId = UUID.randomUUID().toString();
        projectAnalysisWriter.createYet(analysisId, repoUrl, user);

        // 6) SQS enqueue (Lambda event 포맷). 실패 시 FAILED 기록 후 502.
        ProjectAnalysisDto.LambdaJobMessage message = ProjectAnalysisDto.LambdaJobMessage.builder()
                .analysisId(analysisId)
                .githubUsername(githubUsername)
                .repoUrl(repoUrl)
                .userPk(String.valueOf(userId))
                .isPrivate(repo.isPrivateRepo())
                .repoSizeMb(sizeKb / 1024.0)
                .encryptedToken(encryptedToken)
                .mode(mode)
                .build();
        try {
            sqsMessageSender.send(analysisQueueUrl, message);
        } catch (Exception e) {
            log.error("[ProjectAnalysisService] SQS 디스패치 실패. analysisId={}", analysisId, e);
            projectAnalysisWriter.markFailed(analysisId, "디스패치 실패");
            throw new RestException(ErrorCode.ANALYSIS_DISPATCH_FAILED, e);
        }

        // 7) IN_PROGRESS 전환
        projectAnalysisWriter.markInProgress(analysisId);
        log.info("[ProjectAnalysisService] dispatched. analysisId={}, repo={}, userId={}", analysisId, repoUrl, userId);
        return ProjectAnalysisDto.StartResponse.builder()
                .analysisId(analysisId)
                .status(AnalysisFlag.IN_PROGRESS.name())
                .build();
    }

    private record OwnerRepo(String owner, String repo) { }

    private OwnerRepo parseOwnerRepo(String repoUrl) {
        try {
            String s = repoUrl.trim()
                    .replaceFirst("^https?://", "")
                    .replaceFirst("\\.git$", "");
            String[] parts = s.split("/");
            if (parts.length < 3 || parts[parts.length - 2].isBlank() || parts[parts.length - 1].isBlank()) {
                throw new IllegalArgumentException("invalid repo url");
            }
            return new OwnerRepo(parts[parts.length - 2], parts[parts.length - 1]);
        } catch (Exception e) {
            throw new RestException(ErrorCode.GLOBAL_INVALID_PARAMETER, "유효한 GitHub 저장소 URL이 아닙니다.");
        }
    }

    // ============================================================
    // 완료 콜백 (Lambda → BE)
    // ============================================================

    /**
     * 분석 완료 콜백 처리(Lambda → BE). 멱등성 보장 — 이미 종료(DONE/FAILED)된 건은 건너뜀.
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeAnalysis(ProjectAnalysisDto.WebhookCompleteRequest dto) {
        MDC.put("analysisId", dto.getAnalysisId());
        try {
            ProjectAnalysis analysis = projectAnalysisRepository.findById(dto.getAnalysisId())
                    .orElseThrow(() -> new RestException(ErrorCode.PROJECT_ANALYSIS_NOT_FOUND));

            if (isTerminal(analysis.getAnalysisFlag())) {
                log.info("[ProjectAnalysisService] completeAnalysis skipped (already {}). analysisId={}",
                        analysis.getAnalysisFlag(), dto.getAnalysisId());
                return;
            }

            boolean success = dto.getStatus() != null
                    && SUCCESS_TOKENS.contains(dto.getStatus().trim().toLowerCase());

            if (success) {
                if (dto.getCdnUrl() == null || dto.getCdnUrl().isBlank()) {
                    log.warn("[ProjectAnalysisService] success with no cdnUrl, forcing FAILED. analysisId={}",
                            dto.getAnalysisId());
                    analysis.markFailed("완료 보고됐으나 결과 CDN URL이 없습니다.");
                    return;
                }
                analysis.markDone(dto.getCdnUrl(), dto.getServiceName());
                log.info("[ProjectAnalysisService] analysis DONE. analysisId={}, service={}",
                        dto.getAnalysisId(), dto.getServiceName());
            } else {
                analysis.markFailed(dto.getErrorMessage());
                log.info("[ProjectAnalysisService] analysis FAILED. analysisId={}, status={}",
                        dto.getAnalysisId(), dto.getStatus());
            }
        } finally {
            MDC.remove("analysisId");
        }
    }

    private boolean isTerminal(AnalysisFlag flag) {
        return flag == AnalysisFlag.DONE || flag == AnalysisFlag.FAILED;
    }
}
