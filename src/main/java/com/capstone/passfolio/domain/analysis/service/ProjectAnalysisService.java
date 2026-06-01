package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.domain.ai.client.AiApiClient;
import com.capstone.passfolio.domain.ai.dto.AiDto;
import com.capstone.passfolio.domain.analysis.dto.ProjectAnalysisDto;
import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.enums.AnalysisFlag;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.domain.analysis.service.BatchProgressTracker.BatchOutcome;
import com.capstone.passfolio.domain.aws.sqs.SqsMessageSender;
import com.capstone.passfolio.domain.github.client.GitHubApiClient;
import com.capstone.passfolio.domain.github.dto.GitHubDto;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
    private final BatchProgressTracker batchProgressTracker;
    private final BatchPhoneStore batchPhoneStore;
    private final ProjectAnalysisSseService sseService;
    private final AiApiClient aiApiClient;
    private final SmsNotifier smsNotifier;

    @Value("${aws.sqs.analysis-queue-url}")
    private String analysisQueueUrl;

    @Value("${analysis.dispatch.max-repo-size-kb}")
    private long maxRepoSizeKb;

    private static final int MAX_BATCH = 3;
    private static final int MAX_ADMIN_TEST_BATCH = 20;

    // ============================================================
    // 디스패치 (FE → BE → Lambda), 다중 repo 배치
    // ============================================================

    /**
     * 배치 분석 시작: 토큰 준비(1회) → 전 repo size 게이트 → admission(N, all-or-none)
     * → TTL 재점검 → 공유키 재암호화 → batchId + Redis 배치 등록 → repo별 디스패치.
     */
    public ProjectAnalysisDto.StartResponse initiateBatch(Long userId, ProjectAnalysisDto.StartRequest request) {
        List<String> repoUrls = request.getRepoUrls();
        if (repoUrls == null || repoUrls.isEmpty() || repoUrls.size() > MAX_BATCH) {
            throw new RestException(ErrorCode.ANALYSIS_BATCH_SIZE_EXCEEDED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RestException(ErrorCode.AUTH_USER_NOT_FOUND));
        String githubUsername = user.getGithubLogin();
        String mode = normalizeMode(request.getMode());

        // 1) 토큰 1회 준비(복호 + TTL 점검)
        String plainToken = tokenPreparer.resolvePlaintextWithTtlCheck(userId);

        // 2) 전 repo size 게이트(하나라도 초과 시 아무것도 디스패치하지 않고 거부)
        List<RepoMeta> metas = new ArrayList<>();
        for (String repoUrl : repoUrls) {
            OwnerRepo or = parseOwnerRepo(repoUrl);
            GitHubDto.ApiRepo repo = gitHubApiClient.fetchRepo(plainToken, or.owner(), or.repo());
            long sizeKb = repo.getSize() != null ? repo.getSize() : 0L;
            if (sizeKb > maxRepoSizeKb) {
                throw new RestException(ErrorCode.ANALYSIS_REPO_SIZE_EXCEEDED);
            }
            metas.add(new RepoMeta(repoUrl, sizeKb, repo.isPrivateRepo()));
        }

        // 3) admission(N건 한꺼번에) — all-or-none
        admissionPacer.acquireOrThrow(metas.size());

        // 4) 전송 직전 TTL 재점검 + 재암호화(1회)
        tokenPreparer.assertSufficientTtl(userId);
        String encryptedToken = tokenPreparer.reencryptForLambda(plainToken);

        // 5) 배치 등록 + 완료 SMS용 phone transient 저장(선택, DB 미저장)
        String batchId = UUID.randomUUID().toString();
        batchProgressTracker.createBatch(batchId, metas.size());
        batchPhoneStore.store(batchId, request.getPhone());

        // 6) repo별 디스패치
        List<ProjectAnalysisDto.DispatchedItem> dispatched = new ArrayList<>();
        BatchOutcome syncOutcome = null;
        for (RepoMeta m : metas) {
            String analysisId = UUID.randomUUID().toString();
            projectAnalysisWriter.createYet(batchId, analysisId, m.repoUrl(), user, mode);
            String status;
            try {
                sqsMessageSender.send(analysisQueueUrl, buildMessage(analysisId, githubUsername, m, userId, encryptedToken, mode));
                projectAnalysisWriter.markInProgress(analysisId);
                status = AnalysisFlag.IN_PROGRESS.name();
            } catch (Exception e) {
                log.error("[ProjectAnalysisService] SQS 디스패치 실패. batchId={}, analysisId={}", batchId, analysisId, e);
                projectAnalysisWriter.markFailed(analysisId, "디스패치 실패");
                syncOutcome = batchProgressTracker.recordTerminal(batchId, true); // 디스패치 실패도 종료 1건
                status = AnalysisFlag.FAILED.name();
            }
            dispatched.add(ProjectAnalysisDto.DispatchedItem.builder()
                    .analysisId(analysisId).repoUrl(m.repoUrl()).status(status).build());
        }

        // 모든 repo가 디스패치 단계에서 실패해 배치가 즉시 종료된 경우(웹훅이 오지 않음) → 여기서 마무리.
        if (syncOutcome != null && syncOutcome.allDone()) {
            handleBatchOutcome(batchId, userId, mode, syncOutcome);
        }

        log.info("[ProjectAnalysisService] batch dispatched. batchId={}, count={}, userId={}", batchId, metas.size(), userId);
        return ProjectAnalysisDto.StartResponse.builder().batchId(batchId).analyses(dispatched).build();
    }

    private record RepoMeta(String repoUrl, long sizeKb, boolean isPrivate) { }

    // ============================================================
    // ADMIN 전용 테스트 디스패치 — 공개 repo 다수를 토큰 없이 동시 디스패치(파이프라인/메트릭 부하 테스트).
    // 기존 /start 흐름 미접촉: 토큰 준비·size 게이트·admission 페이서 우회(동시성 그대로 스트레스,
    // 정밀 size 가드는 Lambda p0이 보유), dominant_fallback=true(핸들↔git신원 불일치 흡수),
    // mode=STEP(handleBatchOutcome의 FastAPI 핸드오프는 NONSTOP만 → 자동 스킵).
    // ============================================================
    public ProjectAnalysisDto.AdminTestBatchResponse initiateAdminTestBatch(
            UserPrincipal caller, ProjectAnalysisDto.AdminTestBatchRequest request) {
        assertAdmin(caller);
        List<String> repoUrls = request.getRepoUrls();
        if (repoUrls == null || repoUrls.isEmpty() || repoUrls.size() > MAX_ADMIN_TEST_BATCH) {
            throw new RestException(ErrorCode.ANALYSIS_BATCH_SIZE_EXCEEDED);
        }
        User adminUser = userRepository.findById(caller.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.AUTH_USER_NOT_FOUND));
        String mode = normalizeMode(request.getMode() == null || request.getMode().isBlank() ? "STEP" : request.getMode());

        String batchId = UUID.randomUUID().toString();
        batchProgressTracker.createBatch(batchId, repoUrls.size());

        List<ProjectAnalysisDto.AdminDispatchedItem> dispatched = new ArrayList<>();
        BatchOutcome syncOutcome = null;
        int seq = 0;
        for (String repoUrl : repoUrls) {
            OwnerRepo or = parseOwnerRepo(repoUrl);
            String analysisId = UUID.randomUUID().toString();
            projectAnalysisWriter.createYet(batchId, analysisId, repoUrl, adminUser, mode);
            String status;
            try {
                sqsMessageSender.send(analysisQueueUrl, buildAdminMessage(analysisId, or.owner(), repoUrl, caller.getUserId(), mode));
                projectAnalysisWriter.markInProgress(analysisId);
                status = AnalysisFlag.IN_PROGRESS.name();
            } catch (Exception e) {
                log.error("[ProjectAnalysisService] ADMIN test SQS 디스패치 실패. batchId={}, analysisId={}", batchId, analysisId, e);
                projectAnalysisWriter.markFailed(analysisId, "디스패치 실패");
                syncOutcome = batchProgressTracker.recordTerminal(batchId, true);
                status = AnalysisFlag.FAILED.name();
            }
            dispatched.add(ProjectAnalysisDto.AdminDispatchedItem.builder()
                    .analysisId(analysisId).repoUrl(repoUrl).owner(or.owner()).dispatchSeq(seq++).status(status).build());
        }
        if (syncOutcome != null && syncOutcome.allDone()) {
            handleBatchOutcome(batchId, caller.getUserId(), mode, syncOutcome);
        }
        log.info("[ProjectAnalysisService] ADMIN test batch dispatched. batchId={}, count={}, adminId={}",
                batchId, repoUrls.size(), caller.getUserId());
        return ProjectAnalysisDto.AdminTestBatchResponse.builder().batchId(batchId).analyses(dispatched).build();
    }

    private void assertAdmin(UserPrincipal caller) {
        if (caller == null || caller.getRole() == null || caller.getRole() != Role.ADMIN) {
            throw new RestException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    private ProjectAnalysisDto.LambdaJobMessage buildAdminMessage(
            String analysisId, String owner, String repoUrl, Long adminId, String mode) {
        return ProjectAnalysisDto.LambdaJobMessage.builder()
                .analysisId(analysisId)
                .githubUsername(owner)        // dominant 모드라 핸들 불일치여도 최다 기여자로 해석
                .repoUrl(repoUrl)
                .userPk(String.valueOf(adminId))
                .isPrivate(false)
                .repoSizeMb(0.0)              // size 게이트 우회(Lambda p0 정밀 가드가 담당)
                .encryptedToken(null)         // 공개 repo — 토큰 불필요
                .mode(mode)
                .dominantFallback(true)
                .build();
    }

    private ProjectAnalysisDto.LambdaJobMessage buildMessage(
            String analysisId, String githubUsername, RepoMeta m, Long userId, String encryptedToken, String mode) {
        return ProjectAnalysisDto.LambdaJobMessage.builder()
                .analysisId(analysisId)
                .githubUsername(githubUsername)
                .repoUrl(m.repoUrl())
                .userPk(String.valueOf(userId))
                .isPrivate(m.isPrivate())
                .repoSizeMb(m.sizeKb() / 1024.0)
                .encryptedToken(encryptedToken)
                .mode(mode)
                .build();
    }

    private String normalizeMode(String mode) {
        return (mode == null || mode.isBlank()) ? "NONSTOP" : mode.trim().toUpperCase();
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
    // 완료 콜백 (Lambda → BE) + 배치 완료 처리
    // ============================================================

    /**
     * 분석 완료 콜백. DB 변경은 writer 트랜잭션에서 커밋한 뒤(외부호출은 txn 밖에서 안전하게)
     * 개별 SSE + 배치 카운터 갱신 + all-done 처리(전원 성공·NONSTOP → FastAPI / 배치 SSE / SMS).
     * 멱등(이미 종료면 skip). 미존재면 PROJECT_ANALYSIS_NOT_FOUND.
     */
    public void completeAnalysis(ProjectAnalysisDto.WebhookCompleteRequest dto) {
        MDC.put("analysisId", dto.getAnalysisId());
        try {
            // 1) DB 변경 + 커밋 (트랜잭션 경계는 writer)
            ProjectAnalysisWriter.CompletionResult r = projectAnalysisWriter.applyCompletion(dto);
            if (r == null) {
                log.info("[ProjectAnalysisService] completeAnalysis skipped (already terminal). analysisId={}",
                        dto.getAnalysisId());
                return;
            }
            log.info("[ProjectAnalysisService] analysis {}. analysisId={}", r.statusName(), r.analysisId());

            // 2) 커밋 후 통지(외부호출 — txn 밖)
            sseService.pushAnalysis(r.userId(), ProjectAnalysisDto.AnalysisSsePayload.builder()
                    .analysisId(r.analysisId()).batchId(r.batchId()).status(r.statusName())
                    .repoUrl(r.repoUrl()).cdnUrl(r.cdnUrl()).serviceName(r.serviceName())
                    .build());

            // 3) 배치 카운터 갱신 + all-done 처리
            if (r.batchId() != null) {
                BatchOutcome outcome = batchProgressTracker.recordTerminal(r.batchId(), r.failed());
                if (outcome.allDone()) {
                    handleBatchOutcome(r.batchId(), r.userId(), r.mode(), outcome);
                }
            }
        } finally {
            MDC.remove("analysisId");
        }
    }

    /** 배치 전체 완료: 전원 성공·NONSTOP이면 FastAPI 핸드오프, 그 외 생략. 배치 SSE + SMS 통지. */
    private void handleBatchOutcome(String batchId, Long userId, String mode, BatchOutcome outcome) {
        List<ProjectAnalysis> repos = projectAnalysisRepository.findByBatchId(batchId);
        boolean portfolioRequested = false;

        if (outcome.allSuccess() && "NONSTOP".equalsIgnoreCase(mode)) {
            try {
                List<AiDto.AnalysisItem> items = repos.stream()
                        .filter(a -> a.getAnalysisFlag() == AnalysisFlag.DONE)
                        .map(a -> AiDto.AnalysisItem.builder()
                                .analysisId(a.getId())
                                .repoUrl(a.getRepoUrl())
                                .resultCdnUrl(a.getResultCdnUrl())
                                .serviceName(a.getServiceName())
                                .build())
                        .toList();
                aiApiClient.requestPortfolioFromAnalyses(
                        AiDto.AnalysisResultsRequest.builder().analyses(items).userId(userId).build());
                portfolioRequested = true;
                log.info("[ProjectAnalysisService] portfolio handoff requested. batchId={}, repos={}", batchId, items.size());
            } catch (Exception e) {
                // FastAPI 실패는 웹훅을 깨지 않음(로그). 배치 SSE/SMS는 그대로.
                log.error("[ProjectAnalysisService] FastAPI 핸드오프 실패. batchId={}", batchId, e);
            }
        }

        sseService.pushBatch(userId, ProjectAnalysisDto.BatchSsePayload.builder()
                .batchId(batchId)
                .status(outcome.allSuccess() ? "ALL_DONE" : "BATCH_FAILED")
                .total(repos.size())
                .failures(outcome.failures())
                .portfolioRequested(portfolioRequested)
                .build());
        smsNotifier.notifyBatchCompleted(userId, batchId, repos.size(), outcome.allSuccess());
    }
}
