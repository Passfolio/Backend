package com.capstone.passfolio.domain.article.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.capstone.passfolio.domain.article.entity.Article;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link ArticleDto} 의 변환/페이지 조립 동작 단위 테스트.
 *
 * <p>설계 검증 명세:
 * <ul>
 *   <li>{@link ArticleDto.UpdateRequest#hasAnyChange()} — 어느 한 필드라도 비-null 이면 true,
 *       전부 null 이면 false.</li>
 *   <li>{@link ArticleDto.ArticlePageRequest#toPageable()} — 기본값(createdAt DESC) 매핑,
 *       대소문자 무관 direction, ASC 도 정상, {@code id DESC} tiebreaker 가 항상 보조 정렬축으로 추가.</li>
 *   <li>{@link ArticleDto.ArticleResponse#from(Article, String)} — 엔티티 → 응답 매핑, fileUrls 는
 *       방어적 복사 ({@code List.copyOf}), writerNickname 은 인자 그대로 전달.</li>
 * </ul>
 */
class ArticleDtoTest {

    // ============================================================== UpdateRequest.hasAnyChange()
    @Nested
    @DisplayName("UpdateRequest.hasAnyChange()")
    class HasAnyChange {

        @Test
        @DisplayName("title 만 비-null → true")
        void onlyTitle_true() {
            ArticleDto.UpdateRequest req = ArticleDto.UpdateRequest.builder()
                    .title("새 제목")
                    .build();
            assertThat(req.hasAnyChange()).isTrue();
        }

        @Test
        @DisplayName("contents 만 비-null → true")
        void onlyContents_true() {
            ArticleDto.UpdateRequest req = ArticleDto.UpdateRequest.builder()
                    .contents("새 본문")
                    .build();
            assertThat(req.hasAnyChange()).isTrue();
        }

        @Test
        @DisplayName("fileUrls 만 비-null (빈 배열) → true (전부 비움 의도)")
        void onlyFileUrlsEmpty_true() {
            ArticleDto.UpdateRequest req = ArticleDto.UpdateRequest.builder()
                    .fileUrls(List.of())
                    .build();
            assertThat(req.hasAnyChange()).isTrue();
        }

        @Test
        @DisplayName("모든 필드 null → false")
        void allNull_false() {
            ArticleDto.UpdateRequest req = ArticleDto.UpdateRequest.builder().build();
            assertThat(req.hasAnyChange()).isFalse();
        }

        @Test
        @DisplayName("다중 필드 비-null → true")
        void multipleNonNull_true() {
            ArticleDto.UpdateRequest req = ArticleDto.UpdateRequest.builder()
                    .title("t")
                    .contents("c")
                    .fileUrls(List.of("https://cdn/x.png"))
                    .build();
            assertThat(req.hasAnyChange()).isTrue();
        }
    }

    // ============================================================== ArticlePageRequest.toPageable()
    @Nested
    @DisplayName("ArticlePageRequest.toPageable(): id DESC tiebreaker + 정렬 매핑")
    class ToPageable {

        @Test
        @DisplayName("기본값 (page=0, size=9, sort=createdAt, direction=DESC) — Pageable 매핑")
        void defaults_areCreatedAtDescWithIdTiebreaker() {
            ArticleDto.ArticlePageRequest req = new ArticleDto.ArticlePageRequest();

            Pageable pageable = req.toPageable();

            assertThat(pageable.getPageNumber()).isEqualTo(0);
            assertThat(pageable.getPageSize()).isEqualTo(9);
            // 1차 정렬: createdAt DESC, 2차 정렬 (tiebreaker): id DESC
            List<Sort.Order> orders = pageable.getSort().toList();
            assertThat(orders).hasSize(2);
            assertThat(orders.get(0).getProperty()).isEqualTo("createdAt");
            assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
            assertThat(orders.get(1).getProperty()).isEqualTo("id");
            assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("direction=ASC 도 1차 정렬에는 적용되지만 id 는 항상 DESC 로 강제 (tiebreaker)")
        void ascDirection_appliesToPrimaryButIdAlwaysDesc() {
            ArticleDto.ArticlePageRequest req = new ArticleDto.ArticlePageRequest();
            req.setDirection("ASC");

            Pageable pageable = req.toPageable();

            List<Sort.Order> orders = pageable.getSort().toList();
            assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.ASC);
            // tiebreaker 는 인덱스 컬럼 순서 ((created_at DESC, id DESC)) 와 일치해야 하므로 항상 DESC.
            assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("direction 소문자 'desc' 도 정상 처리 (case-insensitive)")
        void caseInsensitiveDirection() {
            ArticleDto.ArticlePageRequest req = new ArticleDto.ArticlePageRequest();
            req.setDirection("desc");

            Pageable pageable = req.toPageable();

            assertThat(pageable.getSort().toList().get(0).getDirection())
                    .isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("page/size setter 로 변경된 값이 Pageable 에 반영")
        void pageSizeSetters_reflected() {
            ArticleDto.ArticlePageRequest req = new ArticleDto.ArticlePageRequest();
            req.setPage(5);
            req.setSize(20);

            Pageable pageable = req.toPageable();

            assertThat(pageable.getPageNumber()).isEqualTo(5);
            assertThat(pageable.getPageSize()).isEqualTo(20);
            assertThat(pageable.getOffset()).isEqualTo(5L * 20);
        }
    }

    // ============================================================== ArticleResponse.from()
    @Nested
    @DisplayName("ArticleResponse.from(): 엔티티 → 응답 매핑")
    class ResponseFrom {

        @Test
        @DisplayName("모든 필드 매핑 — id/title/contents/fileUrls/thumbnail/writerId/nickname/시각")
        void mapsAllFields() {
            // Article 은 builder 로 만들고, JPA auditor 가 채울 createdBy/createdAt/lastModifiedAt
            // 은 ReflectionTestUtils 로 직접 채워준다 (단위 테스트).
            Article article = Article.builder()
                    .title("제목")
                    .contents("본문")
                    .fileUrls(new ArrayList<>(List.of(
                            "https://cdn.example.com/a.jpg",
                            "https://cdn.example.com/b.png")))
                    .thumbnail("https://cdn.example.com/a.jpg")
                    .build();
            ReflectionTestUtils.setField(article, "id", 42L);
            article.setCreatedBy(7L);
            LocalDateTime created = LocalDateTime.of(2026, 5, 1, 10, 0);
            LocalDateTime modified = LocalDateTime.of(2026, 5, 2, 11, 0);
            article.setCreatedAt(created);
            article.setLastModifiedAt(modified);

            ArticleDto.ArticleResponse resp = ArticleDto.ArticleResponse.from(article, "admin-nick");

            assertThat(resp.getId()).isEqualTo(42L);
            assertThat(resp.getTitle()).isEqualTo("제목");
            assertThat(resp.getContents()).isEqualTo("본문");
            assertThat(resp.getFileUrls()).containsExactly(
                    "https://cdn.example.com/a.jpg",
                    "https://cdn.example.com/b.png");
            assertThat(resp.getThumbnail()).isEqualTo("https://cdn.example.com/a.jpg");
            assertThat(resp.getWriterId()).isEqualTo(7L);
            assertThat(resp.getWriterNickname()).isEqualTo("admin-nick");
            assertThat(resp.getCreatedAt()).isEqualTo(created);
            assertThat(resp.getLastModifiedAt()).isEqualTo(modified);
        }

        @Test
        @DisplayName("writerNickname null — 작성자 삭제 케이스도 지원")
        void writerNicknameNull_supported() {
            Article article = Article.builder()
                    .title("t").contents("c").fileUrls(new ArrayList<>()).build();
            ArticleDto.ArticleResponse resp = ArticleDto.ArticleResponse.from(article, null);
            assertThat(resp.getWriterNickname()).isNull();
        }

        @Test
        @DisplayName("fileUrls 는 방어적 복사 — 응답 List 를 mutate 해도 엔티티에 누설되지 않는다")
        void fileUrlsAreDefensivelyCopied() {
            ArrayList<String> mutableUrls = new ArrayList<>(List.of("https://cdn/a.jpg"));
            Article article = Article.builder()
                    .title("t").contents("c").fileUrls(mutableUrls).build();

            ArticleDto.ArticleResponse resp = ArticleDto.ArticleResponse.from(article, "n");

            // List.copyOf 결과는 immutable — add 시 UnsupportedOperationException 이거나
            // 최소한 엔티티의 컬렉션과 같은 인스턴스가 아니어야 한다.
            assertThat(resp.getFileUrls()).isNotSameAs(mutableUrls);
        }

        @Test
        @DisplayName("빈 fileUrls 도 빈 List 로 매핑 (null 이 아님)")
        void emptyFileUrls_returnsEmptyList() {
            Article article = Article.builder()
                    .title("t").contents("c").fileUrls(new ArrayList<>()).build();

            ArticleDto.ArticleResponse resp = ArticleDto.ArticleResponse.from(article, "n");

            assertThat(resp.getFileUrls()).isNotNull().isEmpty();
        }
    }
}
