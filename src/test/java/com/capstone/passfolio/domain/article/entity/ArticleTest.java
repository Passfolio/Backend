package com.capstone.passfolio.domain.article.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link Article} 엔티티의 도메인 동작 단위 테스트.
 *
 * <p>설계 검증 명세 (요구사항 R3/R5/R7 + 설계 §"helpers"):
 * <ol>
 *   <li>{@code update(title, contents)}: {@code null} 인 필드는 변경하지 않음 — PATCH semantics.</li>
 *   <li>{@code replaceFileUrls(List)}: {@code null} = no-op, 빈 리스트 = 전부 비움 + 썸네일 {@code null},
 *       정상 리스트 = 통째 교체 + 썸네일 재계산. 기존 컬렉션 인스턴스 재사용 (Hibernate persistent
 *       collection 추적 보호).</li>
 *   <li>{@code recomputeThumbnail()}: 첫 이미지 URL 만 썸네일로 선택. 이미지 확장자 화이트리스트는
 *       jpg/jpeg/png/gif/webp/bmp/svg 7종. query string / fragment 가 있어도 path 기준 확장자
 *       추출. 비-이미지 first-element 인 경우에도 뒤쪽에 이미지가 있으면 선택. 이미지가 하나도
 *       없으면 {@code null}.</li>
 * </ol>
 *
 * <p>스프링 컨텍스트/JPA 영속화 없이 순수 POJO 동작만 검증한다 (단위 테스트 원칙).
 */
class ArticleTest {

    private static final String CONTENT = "본문";
    private static final String TITLE = "제목";

    private static Article newArticle(List<String> initialUrls) {
        return Article.builder()
                .title(TITLE)
                .contents(CONTENT)
                .fileUrls(initialUrls != null ? new ArrayList<>(initialUrls) : new ArrayList<>())
                .build();
    }

    // ============================================================== update()
    @Nested
    @DisplayName("update(): null 필드는 미변경 (PATCH semantics)")
    class Update {

        @Test
        @DisplayName("title/contents 모두 비-null → 양쪽 변경")
        void bothNonNull_updatesBoth() {
            Article a = newArticle(null);
            a.update("새 제목", "새 본문");

            assertThat(a.getTitle()).isEqualTo("새 제목");
            assertThat(a.getContents()).isEqualTo("새 본문");
        }

        @Test
        @DisplayName("title 만 비-null → contents 미변경")
        void onlyTitle_updatesTitleOnly() {
            Article a = newArticle(null);
            a.update("새 제목", null);

            assertThat(a.getTitle()).isEqualTo("새 제목");
            assertThat(a.getContents()).isEqualTo(CONTENT);
        }

        @Test
        @DisplayName("contents 만 비-null → title 미변경")
        void onlyContents_updatesContentsOnly() {
            Article a = newArticle(null);
            a.update(null, "새 본문");

            assertThat(a.getTitle()).isEqualTo(TITLE);
            assertThat(a.getContents()).isEqualTo("새 본문");
        }

        @Test
        @DisplayName("양쪽 null → 모두 미변경 (no-op)")
        void bothNull_noChange() {
            Article a = newArticle(null);
            a.update(null, null);

            assertThat(a.getTitle()).isEqualTo(TITLE);
            assertThat(a.getContents()).isEqualTo(CONTENT);
        }
    }

    // ============================================================== replaceFileUrls()
    @Nested
    @DisplayName("replaceFileUrls(): 컬렉션 교체 + 썸네일 재계산")
    class ReplaceFileUrls {

        @Test
        @DisplayName("null 입력 → no-op (PATCH 미변경 의미). 기존 fileUrls/thumbnail 보존")
        void nullInput_isNoOp() {
            Article a = newArticle(List.of(
                    "https://cdn.example.com/a.jpg",
                    "https://cdn.example.com/b.png"));
            a.recomputeThumbnail();
            String thumbnailBefore = a.getThumbnail();

            a.replaceFileUrls(null);

            assertThat(a.getFileUrls()).containsExactly(
                    "https://cdn.example.com/a.jpg",
                    "https://cdn.example.com/b.png");
            assertThat(a.getThumbnail()).isEqualTo(thumbnailBefore);
        }

        @Test
        @DisplayName("빈 리스트 → 전부 비움 + 썸네일 null")
        void emptyList_clearsAllAndThumbnailNull() {
            Article a = newArticle(List.of("https://cdn.example.com/a.jpg"));
            a.recomputeThumbnail();
            assertThat(a.getThumbnail()).isNotNull();

            a.replaceFileUrls(Collections.emptyList());

            assertThat(a.getFileUrls()).isEmpty();
            assertThat(a.getThumbnail()).isNull();
        }

        @Test
        @DisplayName("정상 리스트 → 통째 교체 + 썸네일은 첫 이미지로 재계산")
        void newList_replacesAndRecomputesThumbnail() {
            Article a = newArticle(List.of("https://cdn.example.com/old.jpg"));
            a.recomputeThumbnail();

            a.replaceFileUrls(List.of(
                    "https://cdn.example.com/new1.png",
                    "https://cdn.example.com/new2.gif"));

            assertThat(a.getFileUrls()).containsExactly(
                    "https://cdn.example.com/new1.png",
                    "https://cdn.example.com/new2.gif");
            assertThat(a.getThumbnail()).isEqualTo("https://cdn.example.com/new1.png");
        }

        @Test
        @DisplayName("기존 컬렉션 인스턴스를 재사용한다 (Hibernate persistent collection 추적 보호)")
        void preservesCollectionInstance() {
            Article a = newArticle(List.of("https://cdn.example.com/a.jpg"));
            List<String> originalCollectionRef = a.getFileUrls();

            a.replaceFileUrls(List.of("https://cdn.example.com/b.png"));

            // 동일 List 인스턴스에 대해 clear+addAll 이 일어나야 한다 — Hibernate 가 추적하는
            // persistent collection 을 새 인스턴스로 갈아치우면 detach 가 발생할 수 있음.
            assertThat(a.getFileUrls()).isSameAs(originalCollectionRef);
            assertThat(a.getFileUrls()).containsExactly("https://cdn.example.com/b.png");
        }

        @Test
        @DisplayName("순서 보존 — 입력 순서 그대로 저장 (display_order 의미)")
        void preservesOrder() {
            Article a = newArticle(null);

            a.replaceFileUrls(List.of(
                    "https://cdn.example.com/3.png",
                    "https://cdn.example.com/1.png",
                    "https://cdn.example.com/2.png"));

            assertThat(a.getFileUrls()).containsExactly(
                    "https://cdn.example.com/3.png",
                    "https://cdn.example.com/1.png",
                    "https://cdn.example.com/2.png");
        }
    }

    // ============================================================== recomputeThumbnail()
    @Nested
    @DisplayName("recomputeThumbnail(): 첫 이미지 = 썸네일 (R7)")
    class RecomputeThumbnail {

        @Test
        @DisplayName("이미지가 하나면 그것이 썸네일")
        void singleImage_isThumbnail() {
            Article a = newArticle(List.of("https://cdn.example.com/a.jpg"));
            a.recomputeThumbnail();
            assertThat(a.getThumbnail()).isEqualTo("https://cdn.example.com/a.jpg");
        }

        @Test
        @DisplayName("여러 이미지가 있으면 첫 번째 이미지가 썸네일 (입력 순서 기준)")
        void multipleImages_firstWins() {
            Article a = newArticle(List.of(
                    "https://cdn.example.com/first.gif",
                    "https://cdn.example.com/second.png"));
            a.recomputeThumbnail();
            assertThat(a.getThumbnail()).isEqualTo("https://cdn.example.com/first.gif");
        }

        @Test
        @DisplayName("PDF/MP4 가 앞에 있어도 첫 '이미지' 만 선택 (확장자 화이트리스트)")
        void nonImageFirst_skipsToFirstImage() {
            Article a = newArticle(List.of(
                    "https://cdn.example.com/doc.pdf",
                    "https://cdn.example.com/clip.mp4",
                    "https://cdn.example.com/photo.jpeg"));
            a.recomputeThumbnail();
            assertThat(a.getThumbnail()).isEqualTo("https://cdn.example.com/photo.jpeg");
        }

        @Test
        @DisplayName("이미지가 하나도 없으면 thumbnail = null")
        void noImage_thumbnailNull() {
            Article a = newArticle(List.of(
                    "https://cdn.example.com/doc.pdf",
                    "https://cdn.example.com/clip.mp4"));
            a.recomputeThumbnail();
            assertThat(a.getThumbnail()).isNull();
        }

        @Test
        @DisplayName("빈 리스트 → thumbnail null")
        void emptyList_thumbnailNull() {
            Article a = newArticle(Collections.emptyList());
            a.recomputeThumbnail();
            assertThat(a.getThumbnail()).isNull();
        }

        @Test
        @DisplayName("query string 이 붙은 이미지 URL 도 인식한다 (path 기준 확장자)")
        void imageWithQueryString_recognized() {
            Article a = newArticle(List.of(
                    "https://cdn.example.com/photo.png?v=2&size=large"));
            a.recomputeThumbnail();
            assertThat(a.getThumbnail()).isEqualTo("https://cdn.example.com/photo.png?v=2&size=large");
        }

        @Test
        @DisplayName("fragment 가 붙은 이미지 URL 도 인식한다")
        void imageWithFragment_recognized() {
            Article a = newArticle(List.of("https://cdn.example.com/photo.gif#section"));
            a.recomputeThumbnail();
            assertThat(a.getThumbnail()).isEqualTo("https://cdn.example.com/photo.gif#section");
        }

        @Test
        @DisplayName("확장자 대소문자 무관 (.PNG 도 이미지로 인식)")
        void caseInsensitiveExtension() {
            Article a = newArticle(List.of("https://cdn.example.com/PHOTO.PNG"));
            a.recomputeThumbnail();
            assertThat(a.getThumbnail()).isEqualTo("https://cdn.example.com/PHOTO.PNG");
        }

        @Test
        @DisplayName("점이 디렉터리 경로에 있고 마지막 segment 에 확장자가 없으면 비-이미지")
        void dirHasDot_butFinalSegmentNoExt_isNotImage() {
            // 'v.1' 가 디렉터리 이름이고 'photo' 가 파일명 → 확장자 없음 → 비-이미지
            Article a = newArticle(List.of("https://cdn.example.com/v.1/photo"));
            a.recomputeThumbnail();
            assertThat(a.getThumbnail()).isNull();
        }

        @Test
        @DisplayName("blank/null URL 은 건너뛰고 다음 정상 이미지를 선택")
        void blankAndNullUrls_skipped() {
            List<String> urls = new ArrayList<>(Arrays.asList(
                    "",
                    "   ",
                    null,
                    "https://cdn.example.com/photo.bmp"));
            Article a = newArticle(urls);
            a.recomputeThumbnail();
            assertThat(a.getThumbnail()).isEqualTo("https://cdn.example.com/photo.bmp");
        }

        @Test
        @DisplayName("화이트리스트 7종 모두 인식: jpg/jpeg/png/gif/webp/bmp/svg")
        void whitelistAllSevenExtensions() {
            String[] exts = {"jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"};
            for (String ext : exts) {
                Article a = newArticle(List.of("https://cdn.example.com/file." + ext));
                a.recomputeThumbnail();
                assertThat(a.getThumbnail())
                        .as("확장자 '%s' 는 화이트리스트", ext)
                        .isEqualTo("https://cdn.example.com/file." + ext);
            }
        }
    }
}
