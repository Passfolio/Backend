package com.capstone.passfolio.domain.article.entity;

import com.capstone.passfolio.common.auditor.UserBaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Article 본 엔티티.
 *
 * <p>설계 결정 (요약 — 상세는 {@code 0001-harness-design.md} 참고):
 * <ul>
 *   <li><b>단일 정렬축 ({@code createdAt DESC})</b> — Stats 분리 없이 본 엔티티에 covering index
 *       {@code idx_article_created (created_at DESC, id DESC)} 만 둔다. {@code id DESC} tiebreaker는
 *       offset-pagination 스킬의 필수 규칙.</li>
 *   <li><b>{@code fileUrls}는 정규화된 File FK 가 아닌 {@code @ElementCollection} 으로 비정규화</b>
 *       (요구사항 R5). FE가 S3 multipart upload로 직접 받은 CDN URL 리스트를 그대로 저장한다.
 *       삽입 순서를 {@code @OrderColumn(display_order)} 으로 보존해 "첫 이미지 = 썸네일" 규칙을
 *       deterministically 적용 가능하게 한다.</li>
 *   <li><b>{@code thumbnail} 비정규화 컬럼</b> (요구사항 R7) — 목록 응답에서 fileUrls collection을
 *       만지지 않고도 썸네일을 노출하기 위해 본 행에 복사한다. {@link #replaceFileUrls(List)} 가
 *       매 쓰기 시점에 첫 이미지 URL로 재계산한다.</li>
 *   <li><b>{@code contents} 는 PostgreSQL {@code TEXT}</b> — 가변 길이 본문 (제약: TEXT 타입 지정).
 *       {@code @Lob} 대신 {@code columnDefinition = "TEXT"} 를 사용해 oid out-of-line 저장을 피한다.</li>
 *   <li><b>collection table 의 ON DELETE 는 JPA cascade 가 아니라 DB FK CASCADE</b> 에 위임 —
 *       Service 레이어가 {@code deleteAllByIdInBatch} 로 본 행을 bulk delete 할 때 추가 SQL 없이도
 *       embed 행이 함께 제거된다 (N+1 방지 — 제약 조건).</li>
 * </ul>
 *
 * <p>참고: Bean Validation ({@code @NotBlank}, {@code @URL}, length 한도 등) 은 DTO 레이어에서
 * 강제한다. 본 엔티티는 JPA 영속화 관점의 제약 ({@code nullable}, {@code length}) 만 표현한다.
 */
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Table(
        name = "articles",
        indexes = {
                // 목록/피드 hot-path: 최신순 정렬 + id tiebreaker (offset-pagination skill 규칙).
                @Index(name = "idx_article_created", columnList = "created_at DESC, id DESC")
        }
)
public class Article extends UserBaseEntity {

    /** 첫 이미지 = 썸네일 규칙에 사용되는 이미지 확장자 화이트리스트. (file_security.md 와 동일 집합) */
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 글 제목. 길이 상한은 DTO 의 {@code @Size(max=255)} 와 동일하게 본 컬럼에서도 강제한다. */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * 본문. PostgreSQL {@code TEXT} 가변 길이.
     *
     * <p>{@code @Lob} 을 쓰면 일부 드라이버에서 {@code OID} 매핑(out-of-line storage)으로 동작해
     * stream 핸들이 필요해진다. 단순 가변 문자열이 의도이므로 {@code columnDefinition = "TEXT"}
     * 로 직접 지정한다.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contents;

    /**
     * 썸네일 URL (비정규화). {@code fileUrls} 안의 첫 이미지 URL과 동기화된다.
     * 첫 이미지가 없을 경우 {@code null}.
     */
    @Column(length = 2048)
    private String thumbnail;

    /**
     * 글 안에 임베드된 파일들의 CDN URL 리스트.
     *
     * <p>collection table {@code article_file_urls (article_id, display_order, file_url)} 에 보관.
     * 본 엔티티 삭제 시 DB FK ON DELETE CASCADE 로 함께 정리된다 (Hibernate {@code @ElementCollection}
     * 의 기본 동작도 이와 일치하지만 — bulk delete 경로에서도 안전하도록 FK 제약을 명시한다).
     *
     * <p>S3 실제 객체 정리는 Service 레이어가 본 리스트로부터 key 를 derive 한 뒤 batch delete 한다.
     */
    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "article_file_urls",
            joinColumns = @JoinColumn(
                    name = "article_id",
                    nullable = false,
                    foreignKey = @ForeignKey(name = "FK_ARTICLE_FILE_URLS__ARTICLE")
            )
    )
    @OrderColumn(name = "display_order", nullable = false)
    @Column(name = "file_url", nullable = false, length = 2048)
    private List<String> fileUrls = new ArrayList<>();

    // ---------------------------------------------------------------- helpers

    /**
     * 필드 단위 부분 업데이트. {@code null} 인 필드는 변경하지 않는다.
     *
     * <p>{@code fileUrls} 는 별도 {@link #replaceFileUrls(List)} 로 처리한다 — 썸네일 재계산이
     * 동반되어야 하기 때문에 단순 setter 동작과 다르다.
     */
    public void update(String title, String contents) {
        if (title != null) {
            this.title = title;
        }
        if (contents != null) {
            this.contents = contents;
        }
    }

    /**
     * fileUrls 를 통째로 교체하고 썸네일을 재계산한다.
     *
     * <p>{@code newFileUrls == null} 이면 변경하지 않는다 (PATCH 의미). 빈 리스트는 "전부 비움"
     * 의도로 받아들여 썸네일도 {@code null} 로 만든다.
     *
     * <p>orphanRemoval 등의 효과를 위해 기존 컬렉션 인스턴스를 재사용 (clear → addAll). 새 리스트
     * 인스턴스로 교체하면 Hibernate persistent collection 추적이 깨질 수 있다.
     */
    public void replaceFileUrls(List<String> newFileUrls) {
        if (newFileUrls == null) {
            return;
        }
        this.fileUrls.clear();
        this.fileUrls.addAll(newFileUrls);
        recomputeThumbnail();
    }

    /**
     * 썸네일 = {@code fileUrls} 안의 "첫 번째 이미지 URL" (확장자 기준).
     * 이미지가 하나도 없으면 {@code null}.
     *
     * <p>Service 가 별도 경로로 fileUrls 를 직접 변경한 뒤에도 썸네일을 맞추고 싶을 때 호출 가능하도록
     * package-private 가 아닌 public 으로 노출한다.
     */
    public void recomputeThumbnail() {
        this.thumbnail = firstImageUrl(this.fileUrls);
    }

    private static String firstImageUrl(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        for (String url : urls) {
            if (isImageUrl(url)) {
                return url;
            }
        }
        return null;
    }

    private static boolean isImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        // query string / fragment 제거 후 확장자 추출.
        int qIdx = url.indexOf('?');
        String path = (qIdx >= 0) ? url.substring(0, qIdx) : url;
        int hIdx = path.indexOf('#');
        if (hIdx >= 0) {
            path = path.substring(0, hIdx);
        }
        int dotIdx = path.lastIndexOf('.');
        int slashIdx = path.lastIndexOf('/');
        if (dotIdx < 0 || dotIdx < slashIdx) {
            return false;
        }
        String ext = path.substring(dotIdx + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(ext);
    }
}
