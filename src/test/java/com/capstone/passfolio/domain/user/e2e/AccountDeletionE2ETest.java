package com.capstone.passfolio.domain.user.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstone.passfolio.domain.ai.entity.AiJob;
import com.capstone.passfolio.domain.ai.entity.AiJobType;
import com.capstone.passfolio.domain.ai.repository.AiJobRepository;
import com.capstone.passfolio.domain.analysis.entity.ProjectAnalysis;
import com.capstone.passfolio.domain.analysis.entity.RepoAvailability;
import com.capstone.passfolio.domain.analysis.repository.ProjectAnalysisRepository;
import com.capstone.passfolio.domain.analysis.repository.RepoAvailabilityRepository;
import com.capstone.passfolio.domain.github.repository.GitHubTokenRedisRepository;
import com.capstone.passfolio.domain.spec.entity.DevSpec;
import com.capstone.passfolio.domain.spec.repository.DevSpecRepository;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.domain.user.service.AccountDeletionService;
import com.capstone.passfolio.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원탈퇴 Local E2E — 실제 Spring 컨텍스트 + H2(create-drop으로 FK 제약 존재) 기준.
 *
 * <p>검증: USER는 Soft Delete(행 유지 + 익명화), ADMIN은 Hard Delete(행 제거). 양쪽 모두 소유 자식
 * (dev_spec·project_analysis·repo_availability·ai_job)이 0이 되고, FK 제약 위반(예외) 없이 완료된다.
 * 잘못된 삭제 순서면 H2가 FK 위반으로 던지므로, 순서/무결성을 실제로 검증한다.
 *
 * <p>{@code purge}는 {@code @Transactional}이므로, 프로덕션(AuthService.delete의 tx 안에서 managed
 * user 전달)과 동일하게 {@link TransactionTemplate}로 findById+purge를 한 트랜잭션에 묶어 호출한다.
 */
@DisplayName("회원탈퇴 E2E — USER Soft / ADMIN Hard + 자식 정리(FK 무결성)")
class AccountDeletionE2ETest extends AbstractIntegrationTest {

    // Redis 기반 GitHub 토큰 저장소 — getAccessToken은 기본 Optional.empty() → revoke skip.
    @MockitoBean
    private GitHubTokenRedisRepository githubTokenRedisRepository;

    @Autowired private AccountDeletionService accountDeletionService;
    @Autowired private UserRepository userRepository;
    @Autowired private DevSpecRepository devSpecRepository;
    @Autowired private ProjectAnalysisRepository projectAnalysisRepository;
    @Autowired private RepoAvailabilityRepository repoAvailabilityRepository;
    @Autowired private AiJobRepository aiJobRepository;
    @Autowired private PlatformTransactionManager txManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
    }

    /**
     * USER + 소유 자식(dev_spec·project_analysis·repo_availability·ai_job) 시드.
     *
     * <p>한 트랜잭션 안에서 저장한다. {@code DevSpec.createFor(user)}는 {@code @MapsId}로 user_id를 위임받는데,
     * user가 detached면 persist 시 {@code PersistentObjectException}이 난다(프로덕션에선 항상 managed user를
     * 다루는 서비스 트랜잭션 안에서 생성됨). 따라서 시드도 동일하게 단일 트랜잭션으로 묶는다.
     */
    private User seedUserWithChildren(Role role, String suffix, long githubId) {
        return tx.execute(s -> {
            User user = userRepository.saveAndFlush(User.builder()
                    .username("github_" + suffix)
                    .nickname("nick-" + suffix)
                    .profileImageUrl("https://img/" + suffix + ".png")
                    .role(role)
                    .githubId(githubId)
                    .githubLogin("login-" + suffix)
                    .build());
            Long uid = user.getId();

            devSpecRepository.saveAndFlush(DevSpec.createFor(user)); // dev_spec.id == uid (@MapsId)
            projectAnalysisRepository.saveAndFlush(ProjectAnalysis.builder()
                    .id("pa-" + suffix).batchId("b-" + suffix)
                    .repoUrl("https://github.com/o/r-" + suffix).mode("STEP").user(user).build());
            repoAvailabilityRepository.saveAndFlush(RepoAvailability.builder()
                    .id("ra-" + suffix).repoUrl("https://github.com/o/r-" + suffix).user(user).build());
            aiJobRepository.saveAndFlush(AiJob.builder()
                    .userId(uid).aiJobId("aj-" + suffix).type(AiJobType.PORTFOLIO_FROM_PDF).build());
            return user;
        });
    }

    @Test
    @DisplayName("USER 탈퇴 — user 행 유지+익명화, 모든 자식 0, FK 위반 없음")
    void user_withdrawal_softDeletes_and_purges_children() {
        User seeded = seedUserWithChildren(Role.USER, "u1", 1001L);
        Long uid = seeded.getId();
        String originalUsername = seeded.getUsername();

        tx.executeWithoutResult(s -> {
            User managed = userRepository.findById(uid).orElseThrow();
            accountDeletionService.purge(managed);
        });

        // user 행은 유지되되 익명화
        User after = userRepository.findById(uid).orElseThrow();
        assertThat(after.getUsername()).startsWith("deleted_").isNotEqualTo(originalUsername);
        assertThat(after.getNickname()).isEqualTo("(탈퇴한 사용자)");
        assertThat(after.getGithubId()).isNull();
        assertThat(after.getGithubLogin()).isNull();

        // 소유 자식 전부 제거
        assertThat(devSpecRepository.existsById(uid)).isFalse();
        assertThat(projectAnalysisRepository.findByUser_IdOrderByCreatedAtDesc(uid, Pageable.unpaged())).isEmpty();
        assertThat(repoAvailabilityRepository.findByUser_Id(uid)).isEmpty();
        assertThat(aiJobRepository.findByAiJobId("aj-u1")).isEmpty();
    }

    @Test
    @DisplayName("ADMIN 탈퇴 — user 행 Hard Delete + 자식 0, FK 위반 없음")
    void admin_withdrawal_hardDeletes_and_purges_children() {
        User seeded = seedUserWithChildren(Role.ADMIN, "a1", 2001L);
        Long uid = seeded.getId();

        tx.executeWithoutResult(s -> {
            User managed = userRepository.findById(uid).orElseThrow();
            accountDeletionService.purge(managed);
        });

        assertThat(userRepository.findById(uid)).isEmpty(); // hard delete
        assertThat(devSpecRepository.existsById(uid)).isFalse();
        assertThat(projectAnalysisRepository.findByUser_IdOrderByCreatedAtDesc(uid, Pageable.unpaged())).isEmpty();
        assertThat(repoAvailabilityRepository.findByUser_Id(uid)).isEmpty();
        assertThat(aiJobRepository.findByAiJobId("aj-a1")).isEmpty();
    }

    @Test
    @DisplayName("자식 없는 사용자 탈퇴 — 정상(예외 없음)")
    void user_without_children_withdraws_cleanly() {
        User user = userRepository.saveAndFlush(User.builder()
                .username("github_clean").nickname("clean").profileImageUrl("https://img/c.png")
                .role(Role.USER).githubId(3001L).build());
        Long uid = user.getId();

        tx.executeWithoutResult(s -> accountDeletionService.purge(userRepository.findById(uid).orElseThrow()));

        User after = userRepository.findById(uid).orElseThrow();
        assertThat(after.getUsername()).startsWith("deleted_");
    }
}
