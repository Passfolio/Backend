package com.capstone.passfolio.domain.analysis.service;

import com.capstone.passfolio.domain.analysis.dto.RepoAvailabilityDto;
import com.capstone.passfolio.domain.analysis.entity.RepoAvailability;
import com.capstone.passfolio.domain.analysis.entity.enums.RepoAvailabilityStatus;
import com.capstone.passfolio.domain.analysis.repository.RepoAvailabilityRepository;
import com.capstone.passfolio.domain.aws.sqs.SqsMessageSender;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * repo 사전 점검(Precheck) — 선택 repo(≤5)를 precheck 큐로 디스패치해 Lambda가 clone 후
 * 작업트리/.git 크기를 재고, 콜백으로 (user,repo) 가용성(AVAILABLE/DISABLED)을 전환한다.
 * 분석 실행 도메인(ProjectAnalysis)·admin 테스트 경로와 완전히 분리(별도 테이블 repo_availability).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepoAvailabilityService {

    private static final int MAX_PRECHECK_BATCH = 5;

    private final RepoAvailabilityRepository repoAvailabilityRepository;
    private final UserRepository userRepository;
    private final AnalysisTokenPreparer tokenPreparer;
    private final SqsMessageSender sqsMessageSender;
    private final ProjectAnalysisSseService sseService;

    @Value("${aws.sqs.precheck-queue-url}")
    private String precheckQueueUrl;

    /**
     * 선택 repo들을 사전 점검 디스패치. (user,repo)당 1행 upsert → CHECKING → precheck 큐 전송.
     * private repo clone 위해 GitHub 토큰을 1회 복호·재암호화(분석과 동일 — 유효 토큰 필요).
     */
    @Transactional
    public RepoAvailabilityDto.PrecheckStartResponse initiatePrecheck(Long userId, List<String> repoUrls) {
        if (repoUrls == null || repoUrls.isEmpty() || repoUrls.size() > MAX_PRECHECK_BATCH) {
            throw new RestException(ErrorCode.ANALYSIS_PRECHECK_BATCH_SIZE_EXCEEDED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RestException(ErrorCode.AUTH_USER_NOT_FOUND));

        // 토큰 1회 준비(복호 + TTL 점검) → Lambda 전송용 재암호화. 공개/비공개 모두 auth clone.
        String plainToken = tokenPreparer.resolvePlaintextWithTtlCheck(userId);
        String encryptedToken = tokenPreparer.reencryptForLambda(plainToken);

        List<RepoAvailabilityDto.PrecheckItem> items = new ArrayList<>();
        for (String repoUrl : repoUrls) {
            RepoAvailability row = repoAvailabilityRepository.findByUser_IdAndRepoUrl(userId, repoUrl)
                    .map(existing -> {
                        existing.markChecking();
                        return existing;
                    })
                    .orElseGet(() -> RepoAvailability.builder()
                            .id(UUID.randomUUID().toString())
                            .user(user)
                            .repoUrl(repoUrl)
                            .status(RepoAvailabilityStatus.CHECKING)
                            .build());
            repoAvailabilityRepository.save(row);

            try {
                sqsMessageSender.send(precheckQueueUrl, RepoAvailabilityDto.PrecheckJobMessage.builder()
                        .precheckId(row.getId())
                        .repoUrl(repoUrl)
                        .userPk(String.valueOf(userId))
                        .isPrivate(true)              // 토큰으로 auth clone(공개 repo도 정상)
                        .encryptedToken(encryptedToken)
                        .mode("PRECHECK")
                        .build());
            } catch (Exception e) {
                log.error("[RepoAvailability] precheck 디스패치 실패. userId={}, repoUrl={}", userId, repoUrl, e);
                row.markDisabled("점검 디스패치 실패", null, null);
                repoAvailabilityRepository.save(row);
            }
            items.add(RepoAvailabilityDto.PrecheckItem.from(row));
        }
        log.info("[RepoAvailability] precheck dispatched. userId={}, count={}", userId, repoUrls.size());
        return RepoAvailabilityDto.PrecheckStartResponse.builder().items(items).build();
    }

    /** Lambda 콜백: 점검 결과 반영(멱등) 후 SSE 통지. */
    @Transactional
    public void applyPrecheckResult(RepoAvailabilityDto.PrecheckWebhookRequest dto) {
        RepoAvailability row = repoAvailabilityRepository.findById(dto.getPrecheckId())
                .orElse(null);
        if (row == null) {
            log.warn("[RepoAvailability] precheck 콜백 대상 없음. precheckId={}", dto.getPrecheckId());
            return;
        }
        RepoAvailabilityStatus status = RepoAvailabilityDto.parseStatus(dto.getStatus());
        if (status == RepoAvailabilityStatus.AVAILABLE) {
            row.markAvailable(dto.getWorktreeMb(), dto.getGitHistoryMb());
        } else {
            row.markDisabled(dto.getReason(), dto.getWorktreeMb(), dto.getGitHistoryMb());
        }
        repoAvailabilityRepository.save(row);
        log.info("[RepoAvailability] precheck {}. precheckId={}, repoUrl={}", status, row.getId(), row.getRepoUrl());

        // 결과 SSE 통지(best-effort). repo_url + 상태/사이즈/사유 전달.
        Long ownerId = row.getUser() != null ? row.getUser().getId() : null;
        sseService.pushPrecheck(ownerId, RepoAvailabilityDto.PrecheckSsePayload.from(row));
    }

    /** FE 리스트/필터용 — 사용자의 전체 점검 상태. */
    @Transactional(readOnly = true)
    public List<RepoAvailabilityDto.PrecheckItem> listAvailability(Long userId) {
        return repoAvailabilityRepository.findByUser_Id(userId).stream()
                .map(RepoAvailabilityDto.PrecheckItem::from)
                .toList();
    }

    /** 분석 게이트: 해당 (user,repo)가 AVAILABLE이 아니면 거부. (사용자 /start 경로에서만 호출) */
    @Transactional(readOnly = true)
    public void assertAnalyzable(Long userId, String repoUrl) {
        RepoAvailabilityStatus status = repoAvailabilityRepository.findByUser_IdAndRepoUrl(userId, repoUrl)
                .map(RepoAvailability::getStatus)
                .orElse(null);
        if (status != RepoAvailabilityStatus.AVAILABLE) {
            throw new RestException(ErrorCode.ANALYSIS_REPO_NOT_AVAILABLE);
        }
    }
}
