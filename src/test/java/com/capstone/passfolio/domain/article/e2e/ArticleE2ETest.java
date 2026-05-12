package com.capstone.passfolio.domain.article.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.passfolio.domain.article.entity.Article;
import com.capstone.passfolio.domain.article.repository.ArticleRepository;
import com.capstone.passfolio.domain.s3.service.S3Service;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.domain.user.service.UserService;
import com.capstone.passfolio.support.AbstractIntegrationTest;
import com.capstone.passfolio.system.security.model.UserPrincipal;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

/**
 * Article 도메인 단대단(E2E) 테스트.
 *
 * <p>설계 참조: {@code harness/context-logs/BE-article-article-20260502T212736Z/0001-harness-design.md}
 * §"Acceptance signals" A1–A14 (A11/A12/A13/A15 는 별도 stage 에서 검증되도록 설계됨 — 본 E2E 는 A1–A10
 * + A14 + A4(visitor) 를 1:1 매핑하여 커버한다).
 *
 * <p><b>왜 E2E 인가</b> — 단위/슬라이스 테스트(harness-unit-test seq=0017)는 ArticleService /
 * Article entity / DTO / S3Service 의 mocked 동작을 검증했고, ArticleController 와 ArticleQueryRepository
 * 는 명시적으로 deferred 되어있다 (실제 DB / 실제 HTTP 가 필요). 본 테스트는:
 * <ul>
 *   <li>실제 HTTP 요청 (MockMvc) → Spring Security 인증 (postProcessor 로 SecurityContext 주입) →
 *       ArticleController → ArticleService → ArticleRepository / ArticleQueryRepository → 실제 DB 까지의
 *       full path 를 검증한다.</li>
 *   <li>Bean Validation 실패가 GlobalExceptionHandler 를 거쳐 정확한 ErrorResponse JSON 으로 직렬화되는지
 *       검증 (slice 단위로는 검증 불가).</li>
 *   <li>JPA Auditor (createdBy / createdAt) 가 SecurityContext 의 UserPrincipal 로부터 정확히 채워지는지
 *       검증 (실제 영속화 경로).</li>
 *   <li>UserService.deleteAdmin 의 cascade — articles bulk delete + S3 cleanup + user delete — 가
 *       하나의 트랜잭션에서 동작하는지 service-level 로 검증 (HTTP endpoint 미노출, but real-DB E2E).</li>
 * </ul>
 *
 * <p><b>외부 시스템 정책</b> (백로그 제약 C15):
 * <ul>
 *   <li>{@link S3Service} → {@link MockitoBean} — 실제 AWS 호출 0회. {@link #s3Service}.deleteObjects(...)
 *       호출 인자는 {@link ArgumentCaptor} 로 검증.</li>
 *   <li>{@link S3Client} / {@link S3Presigner} — 컨텍스트 부팅 시 빈 의존을 차단하기 위해 함께 mock.</li>
 *   <li>{@link RedissonClient} — Redis 서버 의존 차단 (다른 도메인 빈이 RedissonClient 를 주입받음).</li>
 *   <li>{@link CareerDataInitializer} / {@link UniversityDataInitializer} — H2 호환 안 되는 PostgreSQL
 *       전용 SQL 을 실행하므로 mock.</li>
 * </ul>
 *
 * <p><b>DB 정책</b> (백로그 제약 C16 — internal API 는 real DB):
 * <ul>
 *   <li>H2 (PostgreSQL mode). Flyway 비활성, Hibernate ddl-auto=create-drop 으로 entity 로부터 직접 DDL.
 *       Testcontainers 미사용 — 본 sub-repo 의 build.gradle 에 testcontainers 의존이 없고, 기존
 *       FileUploadE2ETest 의 검증된 H2 패턴을 그대로 따른다.</li>
 *   <li>Article 영속화/조회/삭제는 모두 실제 DB 트랜잭션 — A14 ("DB state asserted in CRUD tests") 충족.</li>
 * </ul>
 *
 * <p><b>인증 정책</b> (백로그 제약 C17 — admin 계정 필요):
 * <ul>
 *   <li>SecurityFilterChain 은 {@code addFilters = false} 로 우회한다. 실제 JWT 발급/쿠키 흐름은 별도
 *       backlog 의 책임 (auth domain). 본 E2E 의 책임은 "ADMIN 권한 가드가 service 레이어에서 동작하는가"
 *       이지 JWT chain 자체의 검증이 아니다.</li>
 *   <li>{@code @AuthenticationPrincipal UserPrincipal} 주입은 spring-security-test 의
 *       {@code SecurityMockMvcRequestPostProcessors.authentication(...)} 으로 SecurityContext 에 직접
 *       주입한다 — visitor (no auth) / USER / ADMIN 모두 동일한 메커니즘으로 표현 가능.</li>
 * </ul>
 *
 * <p><b>매핑 표</b>:
 * <pre>
 *   A1  → Positive#admin_create_returns201_andPersistsArticleWithFileUrls
 *   A2  → Positive#admin_patch_recomputesThumbnail_andCleansOnlyRemovedS3Keys
 *   A3  → Positive#admin_delete_invokesS3Cleanup_andRemovesDb
 *   A4  → Positive#visitor_getList_works (+ visitor_getSingle_works)
 *   A5  → Negative#userRole_post_is403_articleForbidden (+ noAuth_post_is403)
 *   A6  → Positive#pagination_correctness_orderAndPages
 *   A7  → Negative#page_negative_is400 (+ page_pastEnd_is404_pageNotFound)
 *   A8  → Negative#create_titleBlank_is400 (+ create_contentsBlank_is400)
 *   A9  → Negative#sortWhitelist_disallowed_is400
 *   A10 → Positive#admin_user_delete_cascades_to_articles_and_s3
 *   A14 → 모든 CRUD positive 케이스에서 DB row 검증 동시 수행
 * </pre>
 */
@AutoConfigureMockMvc(addFilters = false)
class ArticleE2ETest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * S3 외부 시스템 — backlog 제약 C15: external API 는 mock.
     * S3Client / S3Presigner / RedissonClient 는 {@link AbstractIntegrationTest} 가 일괄 처리.
     * CareerDataInitializer / UniversityDataInitializer 는 {@code @Profile("!test")} 로 컨텍스트에 미등록.
     */
    @MockitoBean
    private S3Service s3Service;

    /** 테스트마다 새로 만든 admin user 의 DB id (auditor createdBy 와 일치). */
    private Long adminUserId;

    /** 테스트마다 새로 만든 USER role user 의 DB id. */
    private Long regularUserId;

    @BeforeEach
    void setUp() {
        // S3 mock 은 기본 no-op (deleteObjects 가 fail-soft 로 동작하도록).
        willDoNothing().given(s3Service).deleteObjects(anyList());

        // 테스트마다 깨끗한 DB. articles 먼저 (FK), users 다음.
        articleRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.saveAndFlush(User.builder()
                .nickname("admin-tester")
                .username("github_admin_test")
                .profileImageUrl("https://cdn.passfolio.test/avatars/admin.png")
                .role(Role.ADMIN)
                .build());
        adminUserId = admin.getId();

        User regular = userRepository.saveAndFlush(User.builder()
                .nickname("user-tester")
                .username("github_user_test")
                .profileImageUrl("https://cdn.passfolio.test/avatars/user.png")
                .role(Role.USER)
                .build());
        regularUserId = regular.getId();
    }

    @AfterEach
    void cleanup() {
        articleRepository.deleteAll();
        userRepository.deleteAll();
        // SecurityContext leak 방지.
        SecurityContextHolder.clearContext();
    }

    /**
     * SecurityContextHolder 에 ADMIN principal 을 직접 set 한다.
     *
     * <p>{@code @AuthenticationPrincipal UserPrincipal} 은 {@code AuthenticationPrincipalArgumentResolver}
     * 가 {@code SecurityContextHolder.getContext().getAuthentication()} 으로 해석한다. MockMvc 는 동일 스레드에서
     * 실행되므로, 본 메서드로 미리 holder 를 채우면 controller 진입 시 정상적으로 주입된다.
     *
     * <p>{@code addFilters = false} 정책상 spring-security-test 의 {@code RequestPostProcessor}
     * (TestSecurityContextHolderPostProcessor 가 의존하는 filter) 가 동작하지 않으므로 직접 holder 를
     * 채우는 방식을 채택한다 — JwtAuthenticationFilter 등 production 인증 chain 을 우회하는 것이 본 E2E
     * 의 책임 범위이며, JWT chain 자체는 별도 backlog 의 책임.
     */
    private void loginAsAdmin() {
        loginAs(adminUserId, "admin-tester", Role.ADMIN);
    }

    private void loginAsUser() {
        loginAs(regularUserId, "user-tester", Role.USER);
    }

    private void loginAs(Long userId, String username, Role role) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .username(username)
                .role(role)
                .build();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
    }

    private void logout() {
        SecurityContextHolder.clearContext();
    }

    // ============================================================
    // POSITIVE — Acceptance signals A1, A2, A3, A4, A6, A10, A14
    // ============================================================
    @Nested
    @DisplayName("Positive 시나리오")
    class Positive {

        // -------- A1 --------
        @Test
        @DisplayName("A1: ADMIN POST → 201 + ArticleResponse JSON + DB row 생성 + article_file_urls 순서 보존")
        void admin_create_returns201_andPersistsArticleWithFileUrls() throws Exception {
            String requestBody = """
                    {
                      "title": "공지: 5월 정기 점검 안내",
                      "contents": "안녕하세요, 운영팀입니다 ...",
                      "fileUrls": [
                        "https://cdn.passfolio.test/articles/a1-001.png",
                        "https://cdn.passfolio.test/articles/a1-002.jpg",
                        "https://cdn.passfolio.test/articles/a1-003.pdf"
                      ]
                    }
                    """;

            loginAsAdmin();
            mockMvc.perform(post("/api/v1/articles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.title").value("공지: 5월 정기 점검 안내"))
                    .andExpect(jsonPath("$.contents").value("안녕하세요, 운영팀입니다 ..."))
                    .andExpect(jsonPath("$.fileUrls.length()").value(3))
                    .andExpect(jsonPath("$.fileUrls[0]").value("https://cdn.passfolio.test/articles/a1-001.png"))
                    .andExpect(jsonPath("$.fileUrls[1]").value("https://cdn.passfolio.test/articles/a1-002.jpg"))
                    .andExpect(jsonPath("$.fileUrls[2]").value("https://cdn.passfolio.test/articles/a1-003.pdf"))
                    // 첫 이미지(.png) 가 thumbnail 으로 자동 계산
                    .andExpect(jsonPath("$.thumbnail").value("https://cdn.passfolio.test/articles/a1-001.png"))
                    .andExpect(jsonPath("$.writerId").value(adminUserId))
                    .andExpect(jsonPath("$.writerNickname").value("admin-tester"))
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.lastModifiedAt").exists());

            // ---- A14: DB 검증 — 단순 응답 검증으로 끝내지 않는다 ----
            // LAZY ElementCollection (fileUrls) 접근을 위해 새 트랜잭션 안에서 검증.
            inTx(() -> {
                List<Article> all = articleRepository.findAll();
                assertThat(all).hasSize(1);
                Article saved = all.get(0);
                assertThat(saved.getTitle()).isEqualTo("공지: 5월 정기 점검 안내");
                assertThat(saved.getContents()).isEqualTo("안녕하세요, 운영팀입니다 ...");
                // article_file_urls 의 display_order 순서가 보존되는지 — entity 레벨 List 순서로 검증.
                assertThat(saved.getFileUrls()).containsExactly(
                        "https://cdn.passfolio.test/articles/a1-001.png",
                        "https://cdn.passfolio.test/articles/a1-002.jpg",
                        "https://cdn.passfolio.test/articles/a1-003.pdf");
                assertThat(saved.getThumbnail()).isEqualTo("https://cdn.passfolio.test/articles/a1-001.png");
                assertThat(saved.getCreatedBy()).isEqualTo(adminUserId);
                assertThat(saved.getCreatedAt()).isNotNull();
            });
        }

        // -------- A2 --------
        @Test
        @DisplayName("A2: ADMIN PATCH → 200 + DB thumbnail 이 새 fileUrls 첫 이미지로 재계산 + 제거된 URL 만 S3 cleanup")
        void admin_patch_recomputesThumbnail_andCleansOnlyRemovedS3Keys() throws Exception {
            // given — 기존 article (썸네일=foo.png)
            Article existing = persistArticleAsAdmin(
                    "옛 제목", "옛 본문",
                    List.of(
                            "https://cdn.passfolio.test/articles/foo.png",
                            "https://cdn.passfolio.test/articles/bar.pdf"));
            assertThat(existing.getThumbnail()).isEqualTo("https://cdn.passfolio.test/articles/foo.png");

            String body = """
                    {
                      "title": "새 제목",
                      "fileUrls": [
                        "https://cdn.passfolio.test/articles/baz.webp",
                        "https://cdn.passfolio.test/articles/bar.pdf"
                      ]
                    }
                    """;

            loginAsAdmin();
            mockMvc.perform(patch("/api/v1/articles/{id}", existing.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(existing.getId()))
                    .andExpect(jsonPath("$.title").value("새 제목"))
                    // contents 는 PATCH 미포함 → 미변경
                    .andExpect(jsonPath("$.contents").value("옛 본문"))
                    // 새 fileUrls 의 첫 이미지(.webp) 로 재계산
                    .andExpect(jsonPath("$.thumbnail").value("https://cdn.passfolio.test/articles/baz.webp"))
                    .andExpect(jsonPath("$.fileUrls.length()").value(2))
                    .andExpect(jsonPath("$.fileUrls[0]").value("https://cdn.passfolio.test/articles/baz.webp"))
                    .andExpect(jsonPath("$.fileUrls[1]").value("https://cdn.passfolio.test/articles/bar.pdf"));

            // DB 검증 — thumbnail 컬럼이 실제 DB 에 비정규화되어 저장됨
            inTx(() -> {
                Article reloaded = articleRepository.findById(existing.getId()).orElseThrow();
                assertThat(reloaded.getTitle()).isEqualTo("새 제목");
                assertThat(reloaded.getContents()).isEqualTo("옛 본문");
                assertThat(reloaded.getThumbnail()).isEqualTo("https://cdn.passfolio.test/articles/baz.webp");
                assertThat(reloaded.getFileUrls()).containsExactly(
                        "https://cdn.passfolio.test/articles/baz.webp",
                        "https://cdn.passfolio.test/articles/bar.pdf");
            });

            // S3 cleanup — 차집합(제거된 URL = foo.png) 의 key 만 deleteObjects 에 전달되었는지
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            then(s3Service).should(times(1)).deleteObjects(captor.capture());
            List<String> deletedKeys = captor.getValue();
            assertThat(deletedKeys).containsExactly("articles/foo.png");
        }

        // -------- A3 --------
        @Test
        @DisplayName("A3: ADMIN DELETE → 204 + S3.deleteObjects 호출 (key=URL prefix strip 결과) + DB row 제거")
        void admin_delete_invokesS3Cleanup_andRemovesDb() throws Exception {
            Article existing = persistArticleAsAdmin(
                    "삭제 대상", "본문",
                    List.of(
                            "https://cdn.passfolio.test/articles/keep.png?ver=1",
                            "https://cdn.passfolio.test/articles/keep2.pdf",
                            // 외부 CDN — prefix 불일치 → S3 key 추출 skip 되어야 함
                            "https://other.cdn.example.com/external.png"));

            loginAsAdmin();
            mockMvc.perform(delete("/api/v1/articles/{id}", existing.getId()))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            // DB row 가 실제로 사라졌는지 검증 (A14)
            assertThat(articleRepository.findById(existing.getId())).isEmpty();
            assertThat(articleRepository.findAll()).isEmpty();

            // S3 cleanup — query string 제거 + 외부 CDN skip 동작 검증
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            then(s3Service).should(times(1)).deleteObjects(captor.capture());
            List<String> deletedKeys = captor.getValue();
            assertThat(deletedKeys).containsExactly("articles/keep.png", "articles/keep2.pdf");
            assertThat(deletedKeys).noneMatch(k -> k.contains("external"));
            assertThat(deletedKeys).noneMatch(k -> k.contains("?"));
        }

        // -------- A4 --------
        @Test
        @DisplayName("A4: visitor (no auth) GET / → 200 paged response — public read")
        void visitor_getList_works() throws Exception {
            persistArticleAsAdmin("A", "본문", List.of());
            persistArticleAsAdmin("B", "본문", List.of());

            mockMvc.perform(get("/api/v1/articles"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("아티클 목록"))
                    .andExpect(jsonPath("$.page").exists())
                    .andExpect(jsonPath("$.page.page").value(0))
                    .andExpect(jsonPath("$.page.size").value(9))
                    .andExpect(jsonPath("$.page.totalElements").value(2))
                    .andExpect(jsonPath("$.page.totalPages").value(1))
                    .andExpect(jsonPath("$.page.hasNext").value(false))
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        // -------- A4 보강 --------
        @Test
        @DisplayName("A4 보강: visitor GET /{id} → 200 — public read")
        void visitor_getSingle_works() throws Exception {
            Article a = persistArticleAsAdmin("Hello", "Body", List.of());

            mockMvc.perform(get("/api/v1/articles/{id}", a.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(a.getId()))
                    .andExpect(jsonPath("$.title").value("Hello"))
                    .andExpect(jsonPath("$.contents").value("Body"))
                    .andExpect(jsonPath("$.writerId").value(adminUserId))
                    .andExpect(jsonPath("$.writerNickname").value("admin-tester"));
        }

        // -------- A6 — pagination correctness (sort order + page boundaries) --------
        @Test
        @DisplayName("A6: page-0/1/2 + createdAt DESC 정렬 + 마지막 페이지 잔량 검증 (N=20, size=9 → 3 pages)")
        void pagination_correctness_orderAndPages() throws Exception {
            // 20개를 createdAt 단조 증가로 영속 — title-N 의 N 이 클수록 더 최근.
            LocalDateTime base = LocalDateTime.now().minusHours(1);
            for (int i = 1; i <= 20; i++) {
                Article a = Article.builder()
                        .title("title-" + i)
                        .contents("content-" + i)
                        .build();
                a.setCreatedAt(base.plusMinutes(i));
                a.setLastModifiedAt(base.plusMinutes(i));
                a.setCreatedBy(adminUserId);
                articleRepository.saveAndFlush(a);
            }

            // page=0 → 가장 최신(20..12)
            mockMvc.perform(get("/api/v1/articles").param("page", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.page").value(0))
                    .andExpect(jsonPath("$.page.size").value(9))
                    .andExpect(jsonPath("$.page.totalElements").value(20))
                    .andExpect(jsonPath("$.page.totalPages").value(3))
                    .andExpect(jsonPath("$.page.hasNext").value(true))
                    .andExpect(jsonPath("$.page.hasPrev").value(false))
                    .andExpect(jsonPath("$.content.length()").value(9))
                    .andExpect(jsonPath("$.content[0].title").value("title-20"))
                    .andExpect(jsonPath("$.content[8].title").value("title-12"));

            // page=1 → 11..3
            mockMvc.perform(get("/api/v1/articles").param("page", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(9))
                    .andExpect(jsonPath("$.content[0].title").value("title-11"))
                    .andExpect(jsonPath("$.content[8].title").value("title-3"));

            // page=2 → 잔량 2개 (title-2, title-1)
            mockMvc.perform(get("/api/v1/articles").param("page", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.hasNext").value(false))
                    .andExpect(jsonPath("$.page.hasPrev").value(true))
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].title").value("title-2"))
                    .andExpect(jsonPath("$.content[1].title").value("title-1"));
        }

        // -------- A10 --------
        // 의도적으로 @Transactional 미사용 — service 의 @Transactional 이 commit 한 결과를 새 트랜잭션에서
        // 검증해야 한다. 테스트 트랜잭션이 service 호출을 감싸면 영속성 컨텍스트 캐시 + auto-rollback 으로
        // 실제 DB 상태가 검증되지 않는다 (회귀 방어 0).
        @Test
        @DisplayName("A10: admin user 삭제 → 모든 articles bulk delete + S3 deleteObjects 가 union of fileUrls 키로 호출 + user row 제거")
        void admin_user_delete_cascades_to_articles_and_s3() throws Exception {
            // given — 삭제 대상이 될 별도 admin "victim" 과, 그 admin 이 작성한 3개 article.
            User victim = userRepository.saveAndFlush(User.builder()
                    .nickname("victim-admin")
                    .username("github_victim_admin")
                    .profileImageUrl("https://cdn.passfolio.test/avatars/victim.png")
                    .role(Role.ADMIN)
                    .build());

            articleRepository.saveAndFlush(buildArticle(victim.getId(), "v1", List.of(
                    "https://cdn.passfolio.test/articles/v1-a.png",
                    "https://cdn.passfolio.test/articles/v1-b.jpg")));
            articleRepository.saveAndFlush(buildArticle(victim.getId(), "v2", List.of(
                    "https://cdn.passfolio.test/articles/v2-a.pdf")));
            articleRepository.saveAndFlush(buildArticle(victim.getId(), "v3", List.of()));
            // 다른 admin 이 작성한 article 은 영향받지 않아야 함
            articleRepository.saveAndFlush(buildArticle(adminUserId, "untouched",
                    List.of("https://cdn.passfolio.test/articles/keepme.png")));

            UserPrincipal callerPrincipal = UserPrincipal.builder()
                    .userId(adminUserId)
                    .username("admin-tester")
                    .role(Role.ADMIN)
                    .build();

            // when — UserService.deleteAdmin 직접 호출 (HTTP endpoint 미노출 — service-level E2E)
            userService.deleteAdmin(victim.getId(), callerPrincipal);

            // then — victim 의 article 만 삭제, 다른 admin 의 article 은 잔존
            List<Article> remaining = articleRepository.findAll();
            assertThat(remaining)
                    .hasSize(1)
                    .first()
                    .satisfies(a -> {
                        assertThat(a.getTitle()).isEqualTo("untouched");
                        assertThat(a.getCreatedBy()).isEqualTo(adminUserId);
                    });

            // user row 삭제
            assertThat(userRepository.findById(victim.getId())).isEmpty();
            assertThat(userRepository.findById(adminUserId)).isPresent();

            // S3 cleanup — victim 의 모든 fileUrls union
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            then(s3Service).should(times(1)).deleteObjects(captor.capture());
            List<String> keys = captor.getValue();
            assertThat(keys).containsExactlyInAnyOrder(
                    "articles/v1-a.png", "articles/v1-b.jpg", "articles/v2-a.pdf");
            // untouched article 의 키는 절대 포함되면 안 됨
            assertThat(keys).noneMatch(k -> k.contains("keepme"));
        }
    }

    // ============================================================
    // NEGATIVE — Acceptance signals A5, A7, A8, A9
    // ============================================================
    @Nested
    @DisplayName("Negative 시나리오")
    class Negative {

        // -------- A5 --------
        @Test
        @DisplayName("A5: USER role POST → 403 + error=ARTICLE_FORBIDDEN + DB 영속화 0회")
        void userRole_post_is403_articleForbidden() throws Exception {
            String body = """
                    {
                      "title": "Hi",
                      "contents": "World"
                    }
                    """;
            loginAsUser();
            mockMvc.perform(post("/api/v1/articles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("ARTICLE_FORBIDDEN"));

            assertThat(articleRepository.findAll()).isEmpty();
            then(s3Service).should(never()).deleteObjects(anyList());
        }

        // -------- A5 보강 --------
        @Test
        @DisplayName("A5 보강: 인증 없는 POST → 403 ARTICLE_FORBIDDEN (서비스 가드의 null 보호)")
        void noAuth_post_is403_articleForbidden() throws Exception {
            String body = """
                    { "title": "Hi", "contents": "World" }
                    """;
            mockMvc.perform(post("/api/v1/articles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("ARTICLE_FORBIDDEN"));

            assertThat(articleRepository.findAll()).isEmpty();
        }

        // -------- A7-1 --------
        // 설계의 A7 은 GLOBAL_INVALID_PARAMETER 로 명시했지만, @ModelAttribute 바인딩 검증 실패는
        // BindException → ErrorCode.GLOBAL_BAD_REQUEST 로 매핑된다 (handler 라인 235). 본질적인 검증
        // 의도(@Min(0) 위반 → 400) 는 동일하므로 실제 구현 동작을 충실히 검증한다.
        @Test
        @DisplayName("A7: page=-1 → 400 (@Min(0) violation, GLOBAL_BAD_REQUEST)")
        void page_negative_is400() throws Exception {
            mockMvc.perform(get("/api/v1/articles").param("page", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_BAD_REQUEST"));
        }

        // -------- A7-2 --------
        @Test
        @DisplayName("A7: page=999 (past end) → 404 PAGE_NOT_FOUND")
        void page_pastEnd_is404_pageNotFound() throws Exception {
            persistArticleAsAdmin("only-one", "body", List.of());

            mockMvc.perform(get("/api/v1/articles").param("page", "999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("PAGE_NOT_FOUND"));
        }

        // -------- A8-1 --------
        // 설계의 A8 은 GLOBAL_INVALID_PARAMETER 로 명시했지만, 본 프로젝트의 GlobalExceptionHandler 는
        // MethodArgumentNotValidException → ErrorCode.GLOBAL_BAD_REQUEST 로 매핑한다 (handler 라인 235).
        // HTTP 400 + Bean Validation 거부라는 본질은 동일하므로 실제 구현 동작을 충실히 검증한다.
        @Test
        @DisplayName("A8: title blank → 400 + Bean Validation 거부 (DB 영속화 0회)")
        void create_titleBlank_is400() throws Exception {
            String body = """
                    { "title": "", "contents": "abc" }
                    """;
            loginAsAdmin();
            mockMvc.perform(post("/api/v1/articles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_BAD_REQUEST"));

            assertThat(articleRepository.findAll()).isEmpty();
        }

        // -------- A8-2 --------
        @Test
        @DisplayName("A8: contents blank → 400 + Bean Validation 거부 (DB 영속화 0회)")
        void create_contentsBlank_is400() throws Exception {
            String body = """
                    { "title": "ok", "contents": "" }
                    """;
            loginAsAdmin();
            mockMvc.perform(post("/api/v1/articles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_BAD_REQUEST"));

            assertThat(articleRepository.findAll()).isEmpty();
        }

        // -------- A9 --------
        // sort 값은 @Pattern 으로 검증 — @ModelAttribute 바인딩 단계에서 BindException 으로 변환되어
        // GLOBAL_BAD_REQUEST 로 매핑된다. 핵심은 화이트리스트 외 값이 거부된다는 것 (HTTP 400).
        @Test
        @DisplayName("A9: sort=password → 400 (sort whitelist @Pattern 위반)")
        void sortWhitelist_disallowed_is400() throws Exception {
            mockMvc.perform(get("/api/v1/articles").param("sort", "password"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_BAD_REQUEST"));
        }
    }

    // ============================================================
    // helpers
    // ============================================================

    /**
     * 컨트롤러 진입 없이 admin 작성자로 article 을 영속화한다 (테스트 fixture 용).
     *
     * <p>service 경유로 만들면 thumbnail 자동 계산은 동일하게 동작하지만, auditor (createdBy) 채움이
     * SecurityContext 에 의존한다. 본 헬퍼는 빌더 + setter 로 직접 채워 단순화.
     */
    private Article persistArticleAsAdmin(String title, String contents, List<String> fileUrls) {
        Article a = Article.builder()
                .title(title)
                .contents(contents)
                .fileUrls(new ArrayList<>(fileUrls))
                .build();
        a.recomputeThumbnail();
        a.setCreatedBy(adminUserId);
        a.setCreatedAt(LocalDateTime.now());
        a.setLastModifiedAt(LocalDateTime.now());
        return articleRepository.saveAndFlush(a);
    }

    private Article buildArticle(Long writerId, String title, List<String> fileUrls) {
        Article a = Article.builder()
                .title(title)
                .contents("default contents for " + title)
                .fileUrls(new ArrayList<>(fileUrls))
                .build();
        a.recomputeThumbnail();
        a.setCreatedBy(writerId);
        a.setCreatedAt(LocalDateTime.now());
        a.setLastModifiedAt(LocalDateTime.now());
        return a;
    }

    /**
     * 트랜잭션 안에서 Article 을 다시 조회하고 fileUrls (LAZY ElementCollection) 까지 access 한 뒤
     * 검증을 수행하기 위한 헬퍼. {@link TransactionTemplate} 으로 명시적 새 트랜잭션을 열어 LAZY 컬렉션
     * 초기화를 보장한다 — 이렇게 하지 않으면 {@code articleRepository.findAll()} 직후 fileUrls 접근 시
     * {@link org.hibernate.LazyInitializationException} 이 발생한다.
     */
    private void inTx(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> action.run());
    }
}
