package com.capstone.passfolio.domain.user.service;

import com.capstone.passfolio.domain.ai.repository.AiJobRepository;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.domain.analysis.repository.RepoAvailabilityRepository;
import com.capstone.passfolio.domain.article.service.ArticleService;
import com.capstone.passfolio.domain.file.service.FileService;
import com.capstone.passfolio.domain.github.client.GitHubOAuthRevokeClient;
import com.capstone.passfolio.domain.github.repository.GitHubTokenRedisRepository;
import com.capstone.passfolio.domain.spec.repository.DevSpecCareerRepository;
import com.capstone.passfolio.domain.spec.repository.DevSpecEducationRepository;
import com.capstone.passfolio.domain.spec.repository.DevSpecRepository;
import com.capstone.passfolio.domain.user.dto.UserSoftDeleteDto;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.config.encryption.AesEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원탈퇴 시 한 사용자의 데이터를 일관되게 정리하는 오케스트레이터.
 *
 * <p>정책:
 * <ul>
 *   <li><b>관련(소유) 데이터는 Hard Bulk Delete</b> — 자식→부모 순서로 명시 삭제(벌크 JPQL은 JPA
 *       cascade/orphanRemoval을 우회하므로 순서를 직접 보장한다).</li>
 *   <li><b>User 행은 role 분기</b> — {@code ADMIN}은 Hard Delete, {@code USER}는 Soft Delete(익명화).</li>
 *   <li>외부 자원: GitHub OAuth grant revoke + Redis 토큰 제거, S3 객체 제거(article/file 서비스 위임).</li>
 * </ul>
 *
 * <p>self-delete({@code AuthService.delete})와 admin-delete({@code UserService.deleteAdmin})가
 * 본 서비스를 공유 호출한다. JWT 블랙리스트/쿠키 정리는 호출부(AuthService)가 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final GitHubTokenRedisRepository githubTokenRedisRepository;
    private final GitHubOAuthRevokeClient githubOAuthRevokeClient;
    private final AesEncryptor aesEncryptor;

    private final ArticleService articleService;
    private final FileService fileService;
    private final ProjectAnalysisRepository projectAnalysisRepository;
    private final RepoAvailabilityRepository repoAvailabilityRepository;
    private final AiJobRepository aiJobRepository;
    private final DevSpecEducationRepository devSpecEducationRepository;
    private final DevSpecCareerRepository devSpecCareerRepository;
    private final DevSpecRepository devSpecRepository;
    private final UserRepository userRepository;

    /**
     * 한 사용자의 관련 데이터를 정리하고, role에 따라 User 행을 Soft/Hard Delete 한다.
     * 단일 트랜잭션으로 묶여, 도중 실패 시 전부 롤백된다.
     *
     * @param user 삭제 대상(호출 트랜잭션에서 managed 상태여야 USER soft-delete의 dirty checking이 적용된다)
     */
    @Transactional
    public void purge(User user) {
        final Long userId = user.getId();

        // 1) GitHub OAuth grant revoke + Redis 토큰 제거 (best-effort)
        revokeGithubGrant(userId);

        // 2) 관련(소유) 데이터 Hard Bulk Delete — 자식→부모 순서
        articleService.deleteAllByWriter(userId);          // article + S3
        fileService.deleteAllByOwner(userId);              // file + S3
        projectAnalysisRepository.deleteAllByUserId(userId);
        repoAvailabilityRepository.deleteAllByUserId(userId);
        devSpecEducationRepository.deleteAllByDevSpecId(userId); // dev_spec.id == user.id (@MapsId)
        devSpecCareerRepository.deleteAllByDevSpecId(userId);
        devSpecRepository.deleteByUserId(userId);
        aiJobRepository.deleteAllByUserId(userId);

        // 3) User 행 — role 분기
        if (user.getRole() == Role.ADMIN) {
            userRepository.deleteByUserId(userId);          // Hard Delete (자식 이미 제거 → FK 위반 없음)
            log.info("[AccountDeletion] ADMIN hard-deleted userId={}", userId);
        } else {
            user.softDelete(UserSoftDeleteDto.of(user));    // Soft Delete (JPA dirty checking)
            log.info("[AccountDeletion] USER soft-deleted userId={}", userId);
        }
    }

    /** GitHub OAuth grant revoke(실패해도 진행) + Redis 토큰 제거. (revokeSession과 동일 로직.) */
    private void revokeGithubGrant(Long userId) {
        githubTokenRedisRepository.getAccessToken(userId).ifPresent(encryptedToken -> {
            try {
                String plainToken = aesEncryptor.decrypt(encryptedToken);
                githubOAuthRevokeClient.revokeGrant(plainToken);
            } catch (Exception e) {
                log.warn("[AccountDeletion] GitHub grant revoke 실패 userId={}: {}", userId, e.getMessage());
            }
            githubTokenRedisRepository.deleteAccessToken(userId);
        });
    }
}
