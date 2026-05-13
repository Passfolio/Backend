package com.capstone.passfolio.domain.article.service;

import com.capstone.passfolio.common.dto.PageDto;
import com.capstone.passfolio.common.util.PageUtils;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Article 도메인 비즈니스 서비스.
 *
 * <p>설계 결정 (요약 — 상세는 {@code 0001-harness-design.md} 참고):
 * <ul>
 *   <li><b>역할 가드는 Service 레이어</b> — 프로젝트 컨벤션에 따라 {@code @PreAuthorize} 대신
 *       {@link #assertAdmin(UserPrincipal)} 가 {@code ARTICLE_FORBIDDEN} 으로 거부한다.
 *       {@code AUTH_FORBIDDEN} 과 의미를 분리해 FE 에서 "ADMIN 아님" 원인을 명확히 알 수 있게 한다.</li>
 *   <li><b>S3 cleanup 순서</b> — DB 삭제 <em>이전</em> 에 S3 batch delete 를 호출한다 (설계 §"Decisions").
 *       S3 fail-soft 정책상 예외가 전파되지 않으며, 잔여 S3 객체는 lifecycle rule 로 회수된다.
 *       반대로 fail-closed (DB 먼저) 면 DB 삭제 실패 시 S3 객체가 orphan 으로 남아 운영상 더 위험.</li>
 *   <li><b>Admin user 삭제 시 bulk delete</b> — {@link #deleteAllByWriter(Long)} 가 작성자의 모든
 *       Article 을 하나의 쿼리로 가져와 fileUrls 를 flatten → 단일 {@code S3Service.deleteObjects} 호출 →
 *       단일 {@code DELETE WHERE created_by = ?} 로 정리. {@code @OneToMany cascade} 의 per-row delete
 *       N+1 을 회피한다.</li>
 *   <li><b>Thumbnail 재계산은 엔티티 위임</b> — {@link Article#replaceFileUrls(List)} 가 fileUrls
 *       교체와 thumbnail 재계산을 원자적으로 수행. 서비스는 호출만 한다.</li>
 *   <li><b>S3 key 추출은 CDN prefix strip</b> — 저장된 fileUrl 은 {@code FileUrlUtils.buildCdnUrl} 결과
 *       (= {@code <cdn-base>/<key>}) 이므로, 본 서비스가 prefix 를 떼어 key 를 복원한다. CDN base 가
 *       미설정이거나 URL 이 prefix 를 갖고 있지 않으면 안전하게 skip — 잘못된 key 로 S3 호출 후
 *       NoSuchKey 를 받는 것보다 비용이 낮다.</li>
 *   <li><b>writerNickname</b> — 단건 응답에만 채운다. 작성자 미존재 (삭제된 admin) 시 {@code null}.
 *       목록 응답({@code ArticlePageResponse})은 의도적으로 미포함 (N+1 회피, 설계 결정).</li>
 *   <li><b>{@code @Transactional} 기본 — 쓰기 메서드는 클래스 레벨 transactional, 읽기 메서드는
 *       메서드 레벨 {@code @Transactional(readOnly = true)} 로 오버라이드</b>. PostgreSQL 의 read-only
 *       트랜잭션 최적화를 활성화한다.</li>
 *   <li><b>{@link #deleteAllByWriter(Long)} 는 {@code REQUIRED} (default)</b> — 호출자(UserService 의
 *       admin-user-delete)와 동일 트랜잭션에서 동작해 user row delete 와 article bulk delete 가 atomic
 *       하게 묶이게 한다. S3 호출은 트랜잭션 외 부수효과지만 fail-soft 라 롤백을 강제하지 않는다.</li>
 * </ul>
 *
 * @see Article
 * @see ArticleRepository
 * @see ArticleQueryRepository
 * @see S3Service#deleteObjects(List)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ArticleService {

    /** 페이지 목록 응답에 포함되는 사람-친화 제목. {@code PageListResponse.title}. */
    private static final String PAGE_TITLE = "아티클 목록";

    private final ArticleRepository articleRepository;
    private final ArticleQueryRepository articleQueryRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    /**
     * CDN base URL ({@code cdn.base-url}). fileUrl → S3 key 변환 시 prefix 제거에 사용된다.
     *
     * <p>{@code FileUrlUtils#buildCdnUrl} 의 역변환 — 같은 프로퍼티를 참조해 양방향 일관성을 유지한다.
     * 미설정 시 {@code null} (key 추출 불가 → 안전하게 skip).
     */
    @Value("${cdn.base-url:#{null}}")
    private String cdnBaseUrl;

    // ============================================================ Create

    /**
     * 게시글 생성. ADMIN 만 호출 가능.
     *
     * @param request   생성 요청 (Bean Validation 통과 후 진입).
     * @param principal 인증 주체 — ADMIN 권한 확인용.
     * @return 생성된 게시글 응답 (writerNickname 포함).
     * @throws RestException {@link ErrorCode#ARTICLE_FORBIDDEN} — 비-ADMIN.
     */
    public ArticleDto.ArticleResponse create(ArticleDto.CreateRequest request, UserPrincipal principal) {
        assertAdmin(principal);

        // 빈 fileUrls 정상 — 설계상 이미지 없는 글도 허용 (썸네일 null).
        List<String> fileUrls = request.getFileUrls() != null
                ? new ArrayList<>(request.getFileUrls())
                : new ArrayList<>();

        Article article = Article.builder()
                .title(request.getTitle())
                .contents(request.getContents())
                .fileUrls(fileUrls)
                .build();
        // builder 가 fileUrls 를 그대로 세팅 — thumbnail 재계산은 별도 호출.
        article.recomputeThumbnail();

        Article saved = articleRepository.save(article);

        // writerId = createdBy (auditor 가 채움). 응답에 nickname 동봉.
        String nickname = lookupNickname(saved.getCreatedBy());
        return ArticleDto.ArticleResponse.from(saved, nickname);
    }

    // ============================================================ Update

    /**
     * 게시글 부분 업데이트. ADMIN 만 호출 가능.
     *
     * <p>{@code title}/{@code contents}/{@code fileUrls} 가 모두 {@code null} 이면 변경 없이 현재 게시글을
     * 그대로 반환한다 ({@link ArticleDto.UpdateRequest#hasAnyChange()}). {@code fileUrls} 가 비-null 이면 컬렉션이
     * 통째로 교체되고 thumbnail 이 재계산된다 ({@link Article#replaceFileUrls(List)}).
     *
     * <p><b>S3 cleanup</b> — 기존 fileUrls 와 새 fileUrls 의 차집합(= 제거된 URL) 만 S3 에서 삭제한다.
     * 신규/유지 URL 은 그대로 둔다. 차집합 계산이 비싼 set 연산이 아니라 단순 contains-check 라
     * fileUrls 크기(설계 한도 50)를 고려해도 무시 가능한 비용.
     *
     * @param id        업데이트 대상 ID.
     * @param request   업데이트 요청.
     * @param principal 인증 주체.
     * @return 업데이트된 게시글 응답. 변경 필드가 없으면 기존 게시글 응답.
     * @throws RestException {@link ErrorCode#ARTICLE_FORBIDDEN} — 비-ADMIN.
     * @throws RestException {@link ErrorCode#ARTICLE_NOT_FOUND} — 대상 없음.
     */
    public ArticleDto.ArticleResponse update(Long id, ArticleDto.UpdateRequest request, UserPrincipal principal) {
        assertAdmin(principal);

        if (!request.hasAnyChange()) {
            Article article = articleRepository.findById(id)
                    .orElseThrow(() -> new RestException(ErrorCode.ARTICLE_NOT_FOUND));
            String nickname = lookupNickname(article.getCreatedBy());
            return ArticleDto.ArticleResponse.from(article, nickname);
        }

        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new RestException(ErrorCode.ARTICLE_NOT_FOUND));

        // S3 cleanup 대상: 기존 - 신규 (제거된 URL 만). request.getFileUrls() == null 이면
        // PATCH 의미상 fileUrls 미변경이므로 cleanup 도 없음.
        List<String> removedUrls = Collections.emptyList();
        if (request.getFileUrls() != null) {
            // 스냅샷 — 엔티티가 replaceFileUrls 호출로 fileUrls 를 mutate 하기 전에 캡처.
            List<String> oldUrls = new ArrayList<>(article.getFileUrls());
            Set<String> newUrls = Set.copyOf(request.getFileUrls());
            removedUrls = oldUrls.stream()
                    .filter(url -> !newUrls.contains(url))
                    .toList();
        }

        // 엔티티 위임 — title/contents 부분 업데이트, fileUrls 교체 + thumbnail 재계산.
        article.update(request.getTitle(), request.getContents());
        article.replaceFileUrls(request.getFileUrls()); // null 이면 no-op (PATCH 의미)

        // S3 cleanup 은 dirty checking 으로 DB 가 commit 되기 전에 호출해도 무방 — fail-soft 이며
        // S3 키가 사라져도 DB 의 fileUrls 는 이미 새 리스트로 바뀐 상태라 inconsistent 하지 않다.
        if (!removedUrls.isEmpty()) {
            List<String> removedKeys = extractS3KeysFromUrls(removedUrls);
            if (!removedKeys.isEmpty()) {
                s3Service.deleteObjects(removedKeys);
            }
        }

        String nickname = lookupNickname(article.getCreatedBy());
        return ArticleDto.ArticleResponse.from(article, nickname);
    }

    // ============================================================ Delete

    /**
     * 단건 삭제. ADMIN 만 호출 가능.
     *
     * <p>순서: <b>(1) S3 batch delete → (2) DB row delete</b>.
     * S3 가 fail-soft 라 한 chunk 실패가 DB 정리를 막지 않는다.
     *
     * @param id        삭제 대상 ID.
     * @param principal 인증 주체.
     * @throws RestException {@link ErrorCode#ARTICLE_FORBIDDEN} — 비-ADMIN.
     * @throws RestException {@link ErrorCode#ARTICLE_NOT_FOUND} — 대상 없음.
     */
    public void delete(Long id, UserPrincipal principal) {
        assertAdmin(principal);

        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new RestException(ErrorCode.ARTICLE_NOT_FOUND));

        // 1) S3 정리 — 엔티티 fileUrls (LAZY 컬렉션) 접근 시점에 SELECT 가 발생.
        List<String> keys = extractS3KeysFromUrls(article.getFileUrls());
        if (!keys.isEmpty()) {
            s3Service.deleteObjects(keys);
        }

        // 2) DB 삭제 — collection table (article_file_urls) 은 FK ON DELETE CASCADE 로 정리.
        articleRepository.delete(article);
    }

    // ============================================================ Read (single)

    /**
     * 단건 조회. 비로그인 visitor 도 호출 가능 (Controller 의 SecurityConfig permitAll).
     *
     * @param id 게시글 ID.
     * @return 게시글 응답 (writerNickname 포함, 작성자 삭제된 경우 null).
     * @throws RestException {@link ErrorCode#ARTICLE_NOT_FOUND} — 대상 없음.
     */
    @Transactional(readOnly = true)
    public ArticleDto.ArticleResponse getById(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new RestException(ErrorCode.ARTICLE_NOT_FOUND));
        String nickname = lookupNickname(article.getCreatedBy());
        return ArticleDto.ArticleResponse.from(article, nickname);
    }

    // ============================================================ Read (page)

    /**
     * 게시글 목록 조회 (offset pagination). 비로그인 visitor 도 호출 가능.
     *
     * <p>2-step covering-index 페이징 ({@link ArticleQueryRepository#searchArticlePage(org.springframework.data.domain.Pageable)}).
     * page 가 totalPages 를 초과하면 {@link PageUtils#validatePageRange(Page)} 가
     * {@link ErrorCode#PAGE_NOT_FOUND} 를 던진다.
     *
     * @param request 페이지 요청 ({@code @ModelAttribute} 바인딩 — Bean Validation 통과 후 진입).
     * @return 페이지 응답 래퍼 ({@link PageDto.PageListResponse}).
     * @throws RestException {@link ErrorCode#PAGE_NOT_FOUND} — page ≥ totalPages.
     */
    @Transactional(readOnly = true)
    public PageDto.PageListResponse<ArticleDto.ArticlePageResponse> list(ArticleDto.ArticlePageRequest request) {
        Page<ArticleDto.ArticlePageResponse> page = articleQueryRepository.searchArticlePage(request.toPageable());
        PageUtils.validatePageRange(page);
        return PageDto.PageListResponse.of(PAGE_TITLE, page);
    }

    // ============================================================ Bulk delete (admin user delete hook)

    /**
     * 특정 작성자가 쓴 모든 Article 을 일괄 삭제한다 (admin user 삭제 훅).
     *
     * <p>호출자: {@code UserService} 의 admin-user-delete 경로 (T14 에서 wiring). user row 삭제
     * <b>이전</b> 에 호출하면 동일 트랜잭션 안에서 article 정리가 끝난 뒤 user 가 삭제되어 외래키
     * 무결성을 보장한다.
     *
     * <p>흐름:
     * <ol>
     *   <li>{@link ArticleRepository#findAllByCreatedBy(Long)} 로 한 번에 조회 (단일 SELECT).</li>
     *   <li>각 Article 의 fileUrls (LAZY) 를 펼쳐 단일 List 로 flatten — N+1 SELECT 가 발생하지만
     *       admin-user-delete 는 hot-path 가 아니므로 허용 (설계 결정).</li>
     *   <li>flatten 된 URL 전체를 S3 key 로 변환 후 단일 {@link S3Service#deleteObjects(List)} 호출.
     *       1000-key chunk 는 S3Service 내부에서 처리.</li>
     *   <li>{@link ArticleRepository#deleteAllByCreatedBy(Long)} 로 단일 {@code DELETE WHERE created_by = ?}.
     *       collection table 행은 FK CASCADE 로 함께 정리.</li>
     * </ol>
     *
     * <p>입력 fileUrls 가 비어있으면 S3 호출은 skip 한다. 작성한 article 이 없으면 양쪽 모두 no-op.
     *
     * @param writerId 삭제 대상 작성자 ID. {@code null} 이면 no-op (방어적).
     */
    public void deleteAllByWriter(Long writerId) {
        if (writerId == null) {
            return;
        }

        List<Article> articles = articleRepository.findAllByCreatedBy(writerId);
        if (articles.isEmpty()) {
            return;
        }

        // fileUrls 평탄화 — LAZY 컬렉션 접근 시점에 SELECT (N+1, hot-path 아님이라 허용).
        List<String> allUrls = articles.stream()
                .flatMap(a -> a.getFileUrls().stream())
                .toList();
        List<String> allKeys = extractS3KeysFromUrls(allUrls);

        if (!allKeys.isEmpty()) {
            s3Service.deleteObjects(allKeys);
        }

        // 단일 DELETE — per-article delete N+1 회피 (설계 결정: cascade 미사용).
        articleRepository.deleteAllByCreatedBy(writerId);

        log.info("[Article] bulk-deleted articles for writerId={}: articles={}, urls={}",
                writerId, articles.size(), allUrls.size());
    }

    // ============================================================ Helpers

    /**
     * 인증 주체가 ADMIN 이 아닐 경우 {@link ErrorCode#ARTICLE_FORBIDDEN} 으로 거부한다.
     *
     * <p>{@code principal == null} 또는 {@code role == null} 도 ADMIN 아님으로 처리. visitor 가 ADMIN
     * 전용 endpoint 에 도달하는 케이스 (SecurityConfig 가 permit 하지 않으면 그 전 단계에서 거르긴 하나
     * 방어적 이중 가드).
     */
    private void assertAdmin(UserPrincipal principal) {
        if (principal == null || principal.getRole() != Role.ADMIN) {
            throw new RestException(ErrorCode.ARTICLE_FORBIDDEN);
        }
    }

    /**
     * 작성자 nickname 조회. 작성자 미존재 시 {@code null}.
     *
     * <p>단건 응답 ({@link ArticleDto.ArticleResponse}) 에만 사용 — 목록 응답은 N+1 회피를 위해
     * nickname 을 빼두었다.
     */
    private String lookupNickname(Long writerId) {
        if (writerId == null) {
            return null;
        }
        return userRepository.findById(writerId).map(User::getNickname).orElse(null);
    }

    /**
     * fileUrl 리스트에서 S3 object key 만 추출한다.
     *
     * <p>저장 형식 — {@code FileUrlUtils.buildCdnUrl(key)} = {@code "<cdn-base>/<key>"}. 본 메서드는
     * cdnBaseUrl prefix 를 떼어 key 를 복원한다. 다음의 경우 해당 URL 은 skip:
     * <ul>
     *   <li>{@code cdnBaseUrl} 미설정 (prefix 매칭 자체가 불가).</li>
     *   <li>URL 이 prefix 로 시작하지 않음 (외부 URL 또는 변형된 URL).</li>
     *   <li>{@code null}/blank 요소.</li>
     * </ul>
     *
     * <p>중복은 {@link S3Service#deleteObjects(List)} 가 내부에서 distinct 처리하므로 본 메서드는
     * 입력 순서를 보존해 그대로 전달한다.
     *
     * @param urls fileUrl 리스트.
     * @return 추출된 S3 key 리스트 (빈 리스트 가능).
     */
    private List<String> extractS3KeysFromUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return Collections.emptyList();
        }
        if (cdnBaseUrl == null || cdnBaseUrl.isBlank()) {
            // CDN 미설정 — fileUrl 이 무엇이든 key 매핑이 불가능하므로 안전하게 skip.
            log.debug("[Article] cdn.base-url 미설정 — S3 key 추출 skip ({} URL 무시)", urls.size());
            return Collections.emptyList();
        }
        // FileUrlUtils 와 동일 정규화 — trailing slash 제거 후 "/" + key 형태로 비교.
        String normalizedBase = cdnBaseUrl.endsWith("/")
                ? cdnBaseUrl.substring(0, cdnBaseUrl.length() - 1)
                : cdnBaseUrl;
        String prefix = normalizedBase + "/";

        List<String> keys = new ArrayList<>(urls.size());
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            if (!url.startsWith(prefix)) {
                // 외부 URL 또는 다른 CDN base — skip (잘못된 key 로 S3 호출하면 불필요한 NoSuchKey).
                log.debug("[Article] S3 key 추출 skip — CDN prefix 불일치: {}", url);
                continue;
            }
            String key = url.substring(prefix.length());
            // query string / fragment 가 있으면 key 에서 분리 (S3 key 자체엔 없음).
            int qIdx = key.indexOf('?');
            if (qIdx >= 0) {
                key = key.substring(0, qIdx);
            }
            int hIdx = key.indexOf('#');
            if (hIdx >= 0) {
                key = key.substring(0, hIdx);
            }
            if (!key.isBlank()) {
                keys.add(key);
            }
        }
        return keys;
    }
}
