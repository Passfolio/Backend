package com.capstone.passfolio.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.capstone.passfolio.common.dto.PageDto;
import com.capstone.passfolio.domain.article.dto.ArticleDto;
import com.capstone.passfolio.domain.article.entity.Article;
import com.capstone.passfolio.domain.article.repository.ArticleQueryRepository;
import com.capstone.passfolio.domain.article.repository.ArticleRepository;
import com.capstone.passfolio.domain.s3.service.S3Service;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import com.capstone.passfolio.system.security.model.UserPrincipal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link ArticleService} 단위 테스트.
 *
 * <p>Spring 컨텍스트 0개. 의존성은 모두 Mockito mock; {@code cdnBaseUrl} 은 {@link ReflectionTestUtils}
 * 로 직접 세팅 (실제 환경에서는 {@code @Value("${cdn.base-url}")}).
 *
 * <p>검증 명세 (acceptance signals A1, A2, A3, A5, A8, A10):
 * <ul>
 *   <li><b>create</b> — ADMIN 만 통과, fileUrls 가 비었어도 정상 (썸네일 null), repository.save 호출,
 *       응답에 닉네임 결합. USER/null principal 은 {@code ARTICLE_FORBIDDEN}.</li>
 *   <li><b>update</b> — ADMIN 만 통과, hasAnyChange=false → {@code GLOBAL_INVALID_PARAMETER},
 *       대상 미존재 → {@code ARTICLE_NOT_FOUND}, fileUrls 변경 시 차집합만 S3 cleanup,
 *       fileUrls null 이면 S3 호출 없음, 부분 필드 PATCH 동작.</li>
 *   <li><b>delete</b> — ADMIN 만 통과, S3 호출 → DB 삭제 순서, fileUrls 비어있으면 S3 호출 없음.</li>
 *   <li><b>getById</b> — 단건 조회 + nickname 결합, 미존재 → {@code ARTICLE_NOT_FOUND}.</li>
 *   <li><b>list</b> — 정상 페이지 통과, page > totalPages → {@code PAGE_NOT_FOUND}.</li>
 *   <li><b>deleteAllByWriter</b> — 단일 SELECT + 단일 S3 batch + 단일 bulk DELETE. writerId null 또는 빈
 *       결과는 no-op. fileUrls flatten 이 정확히 일어나는지.</li>
 *   <li><b>extractS3KeysFromUrls</b> (private, 행위로 검증) — CDN prefix 매칭/불일치 skip, query/fragment
 *       제거, blank/null skip, cdnBaseUrl 미설정 시 전체 skip.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    private static final String CDN_BASE = "https://cdn.example.com";

    @Mock ArticleRepository articleRepository;
    @Mock ArticleQueryRepository articleQueryRepository;
    @Mock UserRepository userRepository;
    @Mock S3Service s3Service;

    @InjectMocks ArticleService articleService;

    private UserPrincipal admin;
    private UserPrincipal user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(articleService, "cdnBaseUrl", CDN_BASE);

        admin = UserPrincipal.builder().userId(1L).username("admin").role(Role.ADMIN).build();
        user = UserPrincipal.builder().userId(2L).username("user").role(Role.USER).build();
    }

    /** 단위 테스트 helper: id/auditor 필드까지 채워넣은 Article 객체 (실제 DB save 결과 시뮬레이션). */
    private static Article persistedArticle(Long id, Long createdBy, List<String> fileUrls) {
        Article article = Article.builder()
                .title("제목")
                .contents("본문")
                .fileUrls(new ArrayList<>(fileUrls != null ? fileUrls : List.of()))
                .build();
        article.recomputeThumbnail();
        ReflectionTestUtils.setField(article, "id", id);
        article.setCreatedBy(createdBy);
        article.setCreatedAt(LocalDateTime.of(2026, 5, 1, 10, 0));
        article.setLastModifiedAt(LocalDateTime.of(2026, 5, 1, 10, 0));
        return article;
    }

    private static User userWithNickname(Long id, String nickname) {
        User u = User.builder().nickname(nickname).username("u").profileImageUrl("p").build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    // ================================================================ create
    @Nested
    @DisplayName("create(): ADMIN 전용 + 엔티티 저장 + 닉네임 결합")
    class Create {

        @Test
        @DisplayName("ADMIN 호출 → save 호출 + 응답에 nickname 포함")
        void admin_savesAndReturnsResponse() {
            ArticleDto.CreateRequest req = ArticleDto.CreateRequest.builder()
                    .title("t").contents("c")
                    .fileUrls(List.of(CDN_BASE + "/a.jpg"))
                    .build();
            given(articleRepository.save(any(Article.class)))
                    .willAnswer(inv -> {
                        Article a = inv.getArgument(0);
                        ReflectionTestUtils.setField(a, "id", 100L);
                        a.setCreatedBy(1L);
                        a.setCreatedAt(LocalDateTime.now());
                        a.setLastModifiedAt(LocalDateTime.now());
                        return a;
                    });
            given(userRepository.findById(1L)).willReturn(Optional.of(userWithNickname(1L, "admin-nick")));

            ArticleDto.ArticleResponse resp = articleService.create(req, admin);

            assertThat(resp.getId()).isEqualTo(100L);
            assertThat(resp.getTitle()).isEqualTo("t");
            assertThat(resp.getContents()).isEqualTo("c");
            assertThat(resp.getFileUrls()).containsExactly(CDN_BASE + "/a.jpg");
            // 첫 이미지가 jpg 이므로 썸네일도 자동으로 채워져야 함 (Article.recomputeThumbnail).
            assertThat(resp.getThumbnail()).isEqualTo(CDN_BASE + "/a.jpg");
            assertThat(resp.getWriterNickname()).isEqualTo("admin-nick");

            then(articleRepository).should(times(1)).save(any(Article.class));
        }

        @Test
        @DisplayName("USER role → ARTICLE_FORBIDDEN, repository 호출 없음")
        void user_throwsForbidden() {
            ArticleDto.CreateRequest req = ArticleDto.CreateRequest.builder()
                    .title("t").contents("c").build();

            assertThatThrownBy(() -> articleService.create(req, user))
                    .isInstanceOf(RestException.class)
                    .satisfies(e -> assertThat(((RestException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ARTICLE_FORBIDDEN));

            then(articleRepository).should(never()).save(any(Article.class));
        }

        @Test
        @DisplayName("principal == null → ARTICLE_FORBIDDEN (visitor 가 admin 진입점에 도달하는 케이스 방어)")
        void nullPrincipal_throwsForbidden() {
            ArticleDto.CreateRequest req = ArticleDto.CreateRequest.builder()
                    .title("t").contents("c").build();

            assertThatThrownBy(() -> articleService.create(req, null))
                    .isInstanceOf(RestException.class);

            then(articleRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("fileUrls null 또는 빈 리스트도 정상 — 썸네일 null 인 응답")
        void noFileUrls_thumbnailNull() {
            ArticleDto.CreateRequest req = ArticleDto.CreateRequest.builder()
                    .title("t").contents("c").fileUrls(null).build();
            given(articleRepository.save(any(Article.class)))
                    .willAnswer(inv -> {
                        Article a = inv.getArgument(0);
                        a.setCreatedBy(1L);
                        return a;
                    });
            given(userRepository.findById(anyLong())).willReturn(Optional.empty());

            ArticleDto.ArticleResponse resp = articleService.create(req, admin);

            assertThat(resp.getThumbnail()).isNull();
            assertThat(resp.getFileUrls()).isEmpty();
        }
    }

    // ================================================================ update
    @Nested
    @DisplayName("update(): PATCH semantics + S3 차집합 cleanup")
    class Update {

        @Test
        @DisplayName("USER → ARTICLE_FORBIDDEN")
        void user_throwsForbidden() {
            ArticleDto.UpdateRequest req = ArticleDto.UpdateRequest.builder().title("x").build();
            assertThatThrownBy(() -> articleService.update(1L, req, user))
                    .isInstanceOf(RestException.class)
                    .satisfies(e -> assertThat(((RestException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ARTICLE_FORBIDDEN));

            then(articleRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("hasAnyChange=false (모든 필드 null) → GLOBAL_INVALID_PARAMETER")
        void noChange_throwsInvalidParameter() {
            ArticleDto.UpdateRequest req = ArticleDto.UpdateRequest.builder().build();
            assertThatThrownBy(() -> articleService.update(1L, req, admin))
                    .isInstanceOf(RestException.class)
                    .satisfies(e -> assertThat(((RestException) e).getErrorCode())
                            .isEqualTo(ErrorCode.GLOBAL_INVALID_PARAMETER));

            then(articleRepository).should(never()).findById(anyLong());
        }

        @Test
        @DisplayName("대상 ID 미존재 → ARTICLE_NOT_FOUND")
        void notFound_throws() {
            ArticleDto.UpdateRequest req = ArticleDto.UpdateRequest.builder().title("x").build();
            given(articleRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> articleService.update(99L, req, admin))
                    .isInstanceOf(RestException.class)
                    .satisfies(e -> assertThat(((RestException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ARTICLE_NOT_FOUND));
        }

        @Test
        @DisplayName("fileUrls 차집합만 S3 cleanup — 제거된 URL 의 key 만 deleteObjects 인자에 포함")
        void fileUrlsDiff_cleansOnlyRemovedKeys() {
            // 기존 fileUrls = [a.jpg, b.png]. 새 fileUrls = [b.png, c.gif]. 제거된 = [a.jpg]
            Article existing = persistedArticle(1L, 1L, List.of(
                    CDN_BASE + "/a.jpg",
                    CDN_BASE + "/b.png"));
            given(articleRepository.findById(1L)).willReturn(Optional.of(existing));
            given(userRepository.findById(1L)).willReturn(Optional.of(userWithNickname(1L, "n")));

            ArticleDto.UpdateRequest req = ArticleDto.UpdateRequest.builder()
                    .fileUrls(List.of(
                            CDN_BASE + "/b.png",
                            CDN_BASE + "/c.gif"))
                    .build();

            articleService.update(1L, req, admin);

            ArgumentCaptor<List<String>> keysCap = ArgumentCaptor.forClass(List.class);
            then(s3Service).should(times(1)).deleteObjects(keysCap.capture());
            assertThat(keysCap.getValue()).containsExactly("a.jpg");

            // 엔티티 fileUrls 가 새 리스트로 교체됨
            assertThat(existing.getFileUrls()).containsExactly(
                    CDN_BASE + "/b.png", CDN_BASE + "/c.gif");
        }

        @Test
        @DisplayName("fileUrls null (PATCH no-op) 이면 S3 deleteObjects 호출 없음")
        void fileUrlsNull_noS3Call() {
            Article existing = persistedArticle(1L, 1L, List.of(CDN_BASE + "/a.jpg"));
            given(articleRepository.findById(1L)).willReturn(Optional.of(existing));
            given(userRepository.findById(1L)).willReturn(Optional.of(userWithNickname(1L, "n")));

            ArticleDto.UpdateRequest req = ArticleDto.UpdateRequest.builder()
                    .title("새 제목")
                    .build();

            articleService.update(1L, req, admin);

            then(s3Service).should(never()).deleteObjects(any());
            assertThat(existing.getTitle()).isEqualTo("새 제목");
        }

        @Test
        @DisplayName("fileUrls = [] (전부 비움) → 모든 기존 URL 의 key 가 cleanup 대상")
        void fileUrlsEmpty_cleansAll() {
            Article existing = persistedArticle(1L, 1L, List.of(
                    CDN_BASE + "/x.jpg",
                    CDN_BASE + "/y.png"));
            given(articleRepository.findById(1L)).willReturn(Optional.of(existing));
            given(userRepository.findById(1L)).willReturn(Optional.of(userWithNickname(1L, "n")));

            ArticleDto.UpdateRequest req = ArticleDto.UpdateRequest.builder()
                    .fileUrls(List.of())
                    .build();

            articleService.update(1L, req, admin);

            ArgumentCaptor<List<String>> keysCap = ArgumentCaptor.forClass(List.class);
            then(s3Service).should(times(1)).deleteObjects(keysCap.capture());
            assertThat(keysCap.getValue()).containsExactlyInAnyOrder("x.jpg", "y.png");
            assertThat(existing.getFileUrls()).isEmpty();
            assertThat(existing.getThumbnail()).isNull();
        }

        @Test
        @DisplayName("title/contents 부분 변경 — Article.update 위임")
        void partialFieldChange() {
            Article existing = persistedArticle(1L, 1L, List.of());
            given(articleRepository.findById(1L)).willReturn(Optional.of(existing));
            given(userRepository.findById(1L)).willReturn(Optional.of(userWithNickname(1L, "n")));

            ArticleDto.UpdateRequest req = ArticleDto.UpdateRequest.builder()
                    .contents("새 본문만")
                    .build();

            ArticleDto.ArticleResponse resp = articleService.update(1L, req, admin);

            assertThat(existing.getContents()).isEqualTo("새 본문만");
            assertThat(existing.getTitle()).isEqualTo("제목"); // 미변경
            assertThat(resp.getContents()).isEqualTo("새 본문만");
        }
    }

    // ================================================================ delete
    @Nested
    @DisplayName("delete(): S3 → DB 순서, ADMIN 전용")
    class Delete {

        @Test
        @DisplayName("USER → ARTICLE_FORBIDDEN, S3/DB 호출 없음")
        void user_throwsForbidden() {
            assertThatThrownBy(() -> articleService.delete(1L, user))
                    .isInstanceOf(RestException.class);

            then(articleRepository).shouldHaveNoInteractions();
            then(s3Service).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ID 미존재 → ARTICLE_NOT_FOUND, S3 호출 없음")
        void notFound_throws() {
            given(articleRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> articleService.delete(99L, admin))
                    .isInstanceOf(RestException.class)
                    .satisfies(e -> assertThat(((RestException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ARTICLE_NOT_FOUND));

            then(s3Service).should(never()).deleteObjects(any());
        }

        @Test
        @DisplayName("정상 삭제 — fileUrls 의 모든 key 가 S3 cleanup 인자, 그 후 articleRepository.delete 호출")
        void happyPath_callsS3ThenDelete() {
            Article existing = persistedArticle(1L, 1L, List.of(
                    CDN_BASE + "/x.jpg",
                    CDN_BASE + "/y.png"));
            given(articleRepository.findById(1L)).willReturn(Optional.of(existing));

            articleService.delete(1L, admin);

            ArgumentCaptor<List<String>> keysCap = ArgumentCaptor.forClass(List.class);
            then(s3Service).should(times(1)).deleteObjects(keysCap.capture());
            assertThat(keysCap.getValue()).containsExactly("x.jpg", "y.png");

            then(articleRepository).should(times(1)).delete(existing);
            // S3 호출이 delete 보다 먼저 일어났는지 보강 검증
            org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(s3Service, articleRepository);
            inOrder.verify(s3Service).deleteObjects(any());
            inOrder.verify(articleRepository).delete(existing);
        }

        @Test
        @DisplayName("fileUrls 가 비어있으면 S3 호출 없음, DB 삭제만 수행")
        void emptyFileUrls_noS3Call() {
            Article existing = persistedArticle(1L, 1L, List.of());
            given(articleRepository.findById(1L)).willReturn(Optional.of(existing));

            articleService.delete(1L, admin);

            then(s3Service).should(never()).deleteObjects(any());
            then(articleRepository).should(times(1)).delete(existing);
        }
    }

    // ================================================================ getById
    @Nested
    @DisplayName("getById(): 단건 조회 + 닉네임 결합")
    class GetById {

        @Test
        @DisplayName("정상 조회 — 응답에 nickname 결합")
        void happy() {
            Article article = persistedArticle(1L, 7L, List.of(CDN_BASE + "/a.jpg"));
            given(articleRepository.findById(1L)).willReturn(Optional.of(article));
            given(userRepository.findById(7L)).willReturn(Optional.of(userWithNickname(7L, "writer")));

            ArticleDto.ArticleResponse resp = articleService.getById(1L);

            assertThat(resp.getId()).isEqualTo(1L);
            assertThat(resp.getWriterNickname()).isEqualTo("writer");
            assertThat(resp.getWriterId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("작성자가 삭제되어 User 미존재 → writerNickname null (no exception)")
        void writerDeleted_nicknameNull() {
            Article article = persistedArticle(1L, 7L, List.of());
            given(articleRepository.findById(1L)).willReturn(Optional.of(article));
            given(userRepository.findById(7L)).willReturn(Optional.empty());

            ArticleDto.ArticleResponse resp = articleService.getById(1L);

            assertThat(resp.getWriterNickname()).isNull();
        }

        @Test
        @DisplayName("미존재 → ARTICLE_NOT_FOUND")
        void notFound_throws() {
            given(articleRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> articleService.getById(999L))
                    .isInstanceOf(RestException.class)
                    .satisfies(e -> assertThat(((RestException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ARTICLE_NOT_FOUND));
        }
    }

    // ================================================================ list
    @Nested
    @DisplayName("list(): 정상 페이지 통과 + page 초과는 PAGE_NOT_FOUND")
    class ListPaged {

        @Test
        @DisplayName("정상 페이지 — PageListResponse 로 래핑되어 반환 (title/메타/content)")
        void happy() {
            Pageable pageable = PageRequest.of(0, 9);
            Page<ArticleDto.ArticlePageResponse> page =
                    new PageImpl<>(List.of(), pageable, 5L);
            given(articleQueryRepository.searchArticlePage(any(Pageable.class)))
                    .willReturn(page);

            ArticleDto.ArticlePageRequest req = new ArticleDto.ArticlePageRequest();
            PageDto.PageListResponse<ArticleDto.ArticlePageResponse> resp =
                    articleService.list(req);

            assertThat(resp.getTitle()).isEqualTo("아티클 목록");
            assertThat(resp.getContent()).isEmpty();
            assertThat(resp.getPage().getTotalElements()).isEqualTo(5L);
        }

        @Test
        @DisplayName("page 가 totalPages 를 초과 → PAGE_NOT_FOUND (PageUtils.validatePageRange)")
        void pastEnd_throws() {
            Pageable pageable = PageRequest.of(999, 9);
            Page<ArticleDto.ArticlePageResponse> page =
                    new PageImpl<>(List.of(), pageable, 20L); // totalPages=3, page=999 → 초과
            given(articleQueryRepository.searchArticlePage(any(Pageable.class)))
                    .willReturn(page);

            ArticleDto.ArticlePageRequest req = new ArticleDto.ArticlePageRequest();
            req.setPage(999);

            assertThatThrownBy(() -> articleService.list(req))
                    .isInstanceOf(RestException.class)
                    .satisfies(e -> assertThat(((RestException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PAGE_NOT_FOUND));
        }
    }

    // ================================================================ deleteAllByWriter
    @Nested
    @DisplayName("deleteAllByWriter(): admin user 삭제 훅 — 단일 SELECT/S3/DELETE")
    class DeleteAllByWriter {

        @Test
        @DisplayName("writerId null → no-op, repository/S3 미호출")
        void nullWriterId_noOp() {
            articleService.deleteAllByWriter(null);

            then(articleRepository).shouldHaveNoInteractions();
            then(s3Service).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("작성한 Article 이 없으면 — DELETE/S3 호출 없음")
        void noArticles_noOp() {
            given(articleRepository.findAllByCreatedBy(7L)).willReturn(List.of());

            articleService.deleteAllByWriter(7L);

            then(s3Service).shouldHaveNoInteractions();
            then(articleRepository).should(never()).deleteAllByCreatedBy(anyLong());
        }

        @Test
        @DisplayName("정상: 단일 findAllByCreatedBy + 단일 deleteObjects + 단일 deleteAllByCreatedBy 호출")
        void happy_singleQueryPath() {
            Article a1 = persistedArticle(1L, 7L, List.of(CDN_BASE + "/a.jpg", CDN_BASE + "/b.png"));
            Article a2 = persistedArticle(2L, 7L, List.of(CDN_BASE + "/c.gif"));
            given(articleRepository.findAllByCreatedBy(7L)).willReturn(List.of(a1, a2));

            articleService.deleteAllByWriter(7L);

            // SELECT 한 번
            then(articleRepository).should(times(1)).findAllByCreatedBy(7L);

            // S3 batch 한 번 — 모든 fileUrl 의 key flatten
            ArgumentCaptor<List<String>> keysCap = ArgumentCaptor.forClass(List.class);
            then(s3Service).should(times(1)).deleteObjects(keysCap.capture());
            assertThat(keysCap.getValue()).containsExactlyInAnyOrder("a.jpg", "b.png", "c.gif");

            // DELETE 한 번
            then(articleRepository).should(times(1)).deleteAllByCreatedBy(7L);
        }

        @Test
        @DisplayName("Article 은 있으나 fileUrls 가 모두 비어있으면 S3 호출 skip, DB DELETE 는 수행")
        void articlesWithoutFileUrls_skipS3() {
            Article a1 = persistedArticle(1L, 7L, List.of());
            given(articleRepository.findAllByCreatedBy(7L)).willReturn(List.of(a1));

            articleService.deleteAllByWriter(7L);

            then(s3Service).should(never()).deleteObjects(any());
            then(articleRepository).should(times(1)).deleteAllByCreatedBy(7L);
        }
    }

    // ================================================================ extractS3KeysFromUrls (행위 검증)
    @Nested
    @DisplayName("S3 key 추출 동작 (행위로 검증) — CDN prefix strip + 외부 URL skip + cdnBase 미설정")
    class ExtractS3Keys {

        @Test
        @DisplayName("CDN prefix 와 일치하지 않는 외부 URL 은 skip — S3 deleteObjects 인자에 포함되지 않음")
        void externalUrl_skipped() {
            Article existing = persistedArticle(1L, 1L, List.of(
                    CDN_BASE + "/internal.jpg",
                    "https://other-cdn.com/external.png"));
            given(articleRepository.findById(1L)).willReturn(Optional.of(existing));

            articleService.delete(1L, admin);

            ArgumentCaptor<List<String>> keysCap = ArgumentCaptor.forClass(List.class);
            then(s3Service).should(times(1)).deleteObjects(keysCap.capture());
            // 외부 URL 은 prefix 불일치 → skip. 내부 URL 만 key 로 변환.
            assertThat(keysCap.getValue()).containsExactly("internal.jpg");
        }

        @Test
        @DisplayName("query string / fragment 가 붙은 fileUrl 은 key 변환 시 제거된다")
        void queryStringAndFragment_strippedFromKey() {
            Article existing = persistedArticle(1L, 1L, List.of(
                    CDN_BASE + "/a.jpg?v=2&size=1",
                    CDN_BASE + "/b.png#section"));
            given(articleRepository.findById(1L)).willReturn(Optional.of(existing));

            articleService.delete(1L, admin);

            ArgumentCaptor<List<String>> keysCap = ArgumentCaptor.forClass(List.class);
            then(s3Service).should(times(1)).deleteObjects(keysCap.capture());
            assertThat(keysCap.getValue()).containsExactly("a.jpg", "b.png");
        }

        @Test
        @DisplayName("blank/null URL 은 skip")
        void blankAndNullUrls_skipped() {
            ArrayList<String> mixed = new ArrayList<>();
            mixed.add(CDN_BASE + "/x.jpg");
            mixed.add(null);
            mixed.add("");
            mixed.add("   ");

            Article existing = persistedArticle(1L, 1L, mixed);
            given(articleRepository.findById(1L)).willReturn(Optional.of(existing));

            articleService.delete(1L, admin);

            ArgumentCaptor<List<String>> keysCap = ArgumentCaptor.forClass(List.class);
            then(s3Service).should(times(1)).deleteObjects(keysCap.capture());
            assertThat(keysCap.getValue()).containsExactly("x.jpg");
        }

        @Test
        @DisplayName("cdnBaseUrl 미설정 (null) → 모든 URL skip → S3 호출 자체가 일어나지 않음")
        void cdnBaseNull_noS3Call() {
            ReflectionTestUtils.setField(articleService, "cdnBaseUrl", null);
            Article existing = persistedArticle(1L, 1L, List.of(
                    "https://cdn.example.com/x.jpg"));
            given(articleRepository.findById(1L)).willReturn(Optional.of(existing));

            articleService.delete(1L, admin);

            then(s3Service).should(never()).deleteObjects(any());
            then(articleRepository).should(times(1)).delete(existing);
        }

        @Test
        @DisplayName("cdnBaseUrl trailing slash 형태도 정상 동작 (정규화)")
        void cdnBaseTrailingSlash_normalizes() {
            ReflectionTestUtils.setField(articleService, "cdnBaseUrl", CDN_BASE + "/");
            Article existing = persistedArticle(1L, 1L, List.of(CDN_BASE + "/x.jpg"));
            given(articleRepository.findById(1L)).willReturn(Optional.of(existing));

            articleService.delete(1L, admin);

            ArgumentCaptor<List<String>> keysCap = ArgumentCaptor.forClass(List.class);
            then(s3Service).should(times(1)).deleteObjects(keysCap.capture());
            assertThat(keysCap.getValue()).containsExactly("x.jpg");
        }
    }

    // ================================================================ 음성 회귀 방어
    @Nested
    @DisplayName("음성 회귀 방어")
    class NoUnexpectedCalls {

        @Test
        @DisplayName("getById 는 S3 도 articleQueryRepository 도 호출하지 않는다")
        void getById_doesNotTouchS3OrQueryRepo() {
            Article article = persistedArticle(1L, 7L, List.of(CDN_BASE + "/a.jpg"));
            given(articleRepository.findById(1L)).willReturn(Optional.of(article));
            given(userRepository.findById(7L)).willReturn(Optional.empty());

            articleService.getById(1L);

            then(s3Service).shouldHaveNoInteractions();
            then(articleQueryRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("list 는 articleRepository.findById 를 호출하지 않는다 (단건 조회 경로 분리)")
        void list_doesNotTouchSingleFindById() {
            given(articleQueryRepository.searchArticlePage(any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 9), 0L));

            articleService.list(new ArticleDto.ArticlePageRequest());

            then(articleRepository).should(never()).findById(anyLong());
        }
    }

    // ================================================================ 안전성: 빈 입력 통합 검증
    @Nested
    @DisplayName("안전성: 빈 입력 / 없는 데이터")
    class EmptyInputs {

        @Test
        @DisplayName("getById 에서 createdBy 가 null 이어도 nickname null 로 안전 처리")
        void getById_nullCreatedBy_safe() {
            Article article = Article.builder()
                    .title("t").contents("c").fileUrls(new ArrayList<>()).build();
            ReflectionTestUtils.setField(article, "id", 1L);
            // createdBy 미설정 (null) — auditor 미작동 시나리오
            given(articleRepository.findById(1L)).willReturn(Optional.of(article));

            assertThatCode(() -> {
                ArticleDto.ArticleResponse resp = articleService.getById(1L);
                assertThat(resp.getWriterNickname()).isNull();
                assertThat(resp.getWriterId()).isNull();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("update 에서 fileUrls 변경했지만 변화가 없으면 (동일 리스트) S3 cleanup 빈 목록 → S3 호출 skip")
        void update_sameFileUrls_noS3Call() {
            List<String> urls = List.of(CDN_BASE + "/x.jpg");
            Article existing = persistedArticle(1L, 1L, urls);
            given(articleRepository.findById(1L)).willReturn(Optional.of(existing));
            given(userRepository.findById(1L)).willReturn(Optional.of(userWithNickname(1L, "n")));

            ArticleDto.UpdateRequest req = ArticleDto.UpdateRequest.builder()
                    .fileUrls(new ArrayList<>(urls)) // 같은 내용
                    .build();

            articleService.update(1L, req, admin);

            then(s3Service).should(never()).deleteObjects(any());
        }
    }
}
