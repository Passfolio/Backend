package com.capstone.passfolio.domain.article.dto;

import com.capstone.passfolio.domain.article.entity.Article;
import com.querydsl.core.annotations.QueryProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Article 도메인 REST DTO 모음.
 *
 * <p>설계 결정 (요약 — 상세는 {@code 0001-harness-design.md} 참고):
 * <ul>
 *   <li><b>요청/응답 DTO 분리</b> — 입력 DTO ({@link CreateRequest}, {@link UpdateRequest})는 Bean
 *       Validation 으로 입력 가드, 응답 DTO ({@link ArticleResponse}, {@link ArticlePageResponse})는
 *       엔티티 → 응답 변환 책임만 진다. 양쪽을 같은 클래스로 합치는 것은 swagger 모호성 + 검증 누설 위험.</li>
 *   <li><b>{@link UpdateRequest} 모든 필드 nullable</b> — PATCH semantics. {@code null} = "변경 없음".
 *       단, 모든 필드가 동시에 {@code null}인 요청은 의미가 없으므로 service 레이어에서 {@link UpdateRequest#hasAnyChange()}
 *       로 검증해 거부한다 (Bean Validation 단계로 끌어올리지 않은 이유: cross-field 검증은 별도
 *       custom validator를 요구하는데 효익 대비 비용이 크다 — service 한 줄 가드면 충분).</li>
 *   <li><b>{@link ArticlePageResponse} 는 {@code @QueryProjection}</b> — QueryDSL constructor projection
 *       으로 컴파일 타임 타입 안전을 얻는다. 목록 응답이라 {@code contents}/{@code fileUrls} 같은 큰
 *       payload는 제외 — 본문은 단건 조회({@link ArticleResponse})에서만 노출.</li>
 *   <li><b>{@link ArticlePageRequest} 단일 정렬축 + 화이트리스트</b> — offset-pagination 스킬 규칙 4
 *       ("정렬 화이트리스트"). 현재 인덱스가 {@code (created_at DESC, id DESC)} 1개뿐이므로
 *       {@code @Pattern("^(createdAt)$")} 으로 createdAt 만 허용한다. 다른 정렬축이 필요해지면 새
 *       covering index를 추가한 뒤 화이트리스트를 확장한다.</li>
 *   <li><b>{@link ArticlePageRequest#toPageable()} 의 id DESC tiebreaker</b> — offset-pagination 스킬
 *       규칙 2: 모든 covering index는 {@code id DESC}로 끝나야 동률 정렬에서 페이지 경계가 흔들리지
 *       않는다. {@link Sort#and(Sort)}로 보조 정렬축을 붙인다. {@code direction}은 정렬 컬럼에만
 *       적용되고 id는 항상 DESC로 고정 (인덱스 컬럼 순서 일치).</li>
 *   <li><b>{@code fileUrls}는 List&lt;@URL String&gt;</b> — 컬렉션 요소 단위 검증.
 *       Hibernate Validator가 {@link spring-boot-starter-validation}에 포함되어 있어 별도 의존성 추가 없이 사용 가능.</li>
 * </ul>
 *
 * <p>모든 nested DTO는 프로젝트 표준 ({@code @Getter @Builder @NoArgsConstructor @AllArgsConstructor}
 * 또는 {@code @Data @NoArgsConstructor}) 패턴을 따른다. {@code @ModelAttribute}로 받아야 하는
 * {@link ArticlePageRequest}는 setter가 필수이므로 {@code @Data}를 쓴다 (다른 도메인의 PageRequest와 동일).
 *
 * @see com.capstone.passfolio.domain.article.entity.Article
 * @see com.capstone.passfolio.common.dto.PageDto.PageListResponse
 */
public class ArticleDto {

    private ArticleDto() {
        // 정적 컨테이너. 인스턴스화 방지.
    }

    // ============================================================== Create

    /**
     * {@code POST /api/v1/articles} 요청 본문.
     *
     * <p>입력 검증:
     * <ul>
     *   <li>{@code title} — {@link NotBlank}, {@link Size}({@code max=255}). 엔티티 컬럼 길이와 일치.</li>
     *   <li>{@code contents} — {@link NotBlank}. 길이 제한 없음 ({@code TEXT}).</li>
     *   <li>{@code fileUrls} — 선택. 최대 50개. 각 요소는 {@link URL} 형식.</li>
     * </ul>
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "게시글 생성 요청")
    public static class CreateRequest {

        @NotBlank(message = "title 은 비어있을 수 없습니다.")
        @Size(max = 255, message = "title 은 255자를 초과할 수 없습니다.")
        @Schema(description = "게시글 제목", example = "공지: 5월 정기 점검 안내")
        private String title;

        @NotBlank(message = "contents 는 비어있을 수 없습니다.")
        @Schema(description = "게시글 본문 (TEXT — 길이 제한 없음)", example = "안녕하세요, ...")
        private String contents;

        @Size(max = 50, message = "fileUrls 는 최대 50개까지 입력할 수 있습니다.")
        @Schema(
                description = "글 안에 임베드된 파일들의 CDN URL 리스트. 첫 이미지가 자동으로 썸네일이 됩니다. 생략 시 빈 배열.",
                example = "[\"https://cdn.example.com/articles/1.png\", \"https://cdn.example.com/articles/2.jpg\"]")
        private List<@URL(message = "fileUrls 요소는 유효한 URL 이어야 합니다.")
                @Size(max = 2048, message = "fileUrls 요소는 2048자를 초과할 수 없습니다.") String> fileUrls;
    }

    // ============================================================== Update

    /**
     * {@code PATCH /api/v1/articles/{id}} 요청 본문.
     *
     * <p>모든 필드가 {@code null}일 수 있으며 {@code null} = "변경하지 않음" 의미.
     * <ul>
     *   <li>{@code title} — 비어있지 않은 문자열을 보내면 변경. {@code null}이면 미변경. 빈 문자열은 검증 실패.</li>
     *   <li>{@code contents} — 위와 동일.</li>
     *   <li>{@code fileUrls} — {@code null}이면 미변경. 빈 배열 {@code []}을 보내면 "전부 비움" 의도로
     *       처리되어 fileUrls와 thumbnail이 비워진다 ({@link Article#replaceFileUrls(List)} 동작).</li>
     * </ul>
     *
     * <p>최소 한 필드는 비-null 이어야 한다 — service 레이어가 {@link #hasAnyChange()}로 가드한다.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "게시글 부분 업데이트 요청. 모든 필드는 선택이며 null = 변경 없음.")
    public static class UpdateRequest {

        @Size(min = 1, max = 255, message = "title 은 1~255자여야 합니다.")
        @Schema(description = "변경할 제목 (선택). null = 변경 없음.", example = "공지: 5월 정기 점검 안내 (수정)")
        private String title;

        @Size(min = 1, message = "contents 는 비어있을 수 없습니다.")
        @Schema(description = "변경할 본문 (선택). null = 변경 없음.", example = "수정된 본문 ...")
        private String contents;

        @Size(max = 50, message = "fileUrls 는 최대 50개까지 입력할 수 있습니다.")
        @Schema(
                description = "변경할 파일 URL 리스트 (선택). null = 변경 없음. 빈 배열 = 전부 비움.",
                example = "[\"https://cdn.example.com/articles/3.webp\"]")
        private List<@URL(message = "fileUrls 요소는 유효한 URL 이어야 합니다.")
                @Size(max = 2048, message = "fileUrls 요소는 2048자를 초과할 수 없습니다.") String> fileUrls;

        /**
         * "최소 한 필드는 비-null" 검증 헬퍼. service 레이어에서 호출.
         */
        public boolean hasAnyChange() {
            return title != null || contents != null || fileUrls != null;
        }
    }

    // ============================================================== Response (single)

    /**
     * 단건 조회 / 생성 / 업데이트 응답 본문.
     *
     * <p>{@code writerNickname}은 service 레이어가 User 단건 조회로 채운다 (단건 path는 N+1이 아님).
     * 작성자가 삭제된 케이스는 {@code null}로 직렬화.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "게시글 단건 응답")
    public static class ArticleResponse {

        @Schema(description = "게시글 ID", example = "1")
        private Long id;

        @Schema(description = "제목", example = "공지: 5월 정기 점검 안내")
        private String title;

        @Schema(description = "본문 (TEXT)")
        private String contents;

        @Schema(description = "파일 URL 리스트 (입력 순서 보존)")
        private List<String> fileUrls;

        @Schema(description = "썸네일 URL — fileUrls 의 첫 이미지. 이미지가 없으면 null.")
        private String thumbnail;

        @Schema(description = "작성자 사용자 ID", example = "42")
        private Long writerId;

        @Schema(description = "작성자 닉네임. 작성자가 삭제된 경우 null.", example = "admin")
        private String writerNickname;

        @Schema(description = "생성 시각")
        private LocalDateTime createdAt;

        @Schema(description = "마지막 수정 시각")
        private LocalDateTime lastModifiedAt;

        /**
         * 엔티티 → 응답 변환. {@code writerNickname}은 service가 별도 주입한다.
         *
         * @param article         원본 엔티티 ({@code fileUrls} 컬렉션이 초기화된 상태여야 함).
         * @param writerNickname  작성자 닉네임 (작성자 미존재 시 {@code null}).
         */
        public static ArticleResponse from(Article article, String writerNickname) {
            return ArticleResponse.builder()
                    .id(article.getId())
                    .title(article.getTitle())
                    .contents(article.getContents())
                    .fileUrls(List.copyOf(article.getFileUrls()))
                    .thumbnail(article.getThumbnail())
                    .writerId(article.getCreatedBy())
                    .writerNickname(writerNickname)
                    .createdAt(article.getCreatedAt())
                    .lastModifiedAt(article.getLastModifiedAt())
                    .build();
        }
    }

    // ============================================================== Response (page item)

    /**
     * 목록(페이지) 응답 항목.
     *
     * <p>QueryDSL {@code @QueryProjection} 으로 컴파일 타임 타입 안전을 얻는다.
     * 목록 응답이므로 {@code contents}와 {@code fileUrls}는 제외 — payload 절감.
     * {@code thumbnail}은 본 엔티티의 비정규화 컬럼이라 join 없이 가져온다.
     *
     * <p>{@code writerNickname}은 목록에서 작성자별 N+1을 피하기 위해 일단 제외한다 — 필요해지면
     * STEP 2 query에서 단일 LEFT JOIN으로 끌어오는 식으로 확장 가능 (별도 task).
     */
    @Getter
    @NoArgsConstructor
    @Schema(description = "게시글 목록 응답 항목 (경량)")
    public static class ArticlePageResponse {

        @Schema(description = "게시글 ID", example = "1")
        private Long id;

        @Schema(description = "제목")
        private String title;

        @Schema(description = "썸네일 URL — fileUrls 의 첫 이미지. 이미지가 없으면 null.")
        private String thumbnail;

        @Schema(description = "생성 시각")
        private LocalDateTime createdAt;

        @Schema(description = "작성자 사용자 ID", example = "42")
        private Long writerId;

        /**
         * QueryDSL constructor projection 전용. {@code @QueryProjection}이 Q-class를 생성한다.
         */
        @QueryProjection
        public ArticlePageResponse(Long id, String title, String thumbnail,
                                   LocalDateTime createdAt, Long writerId) {
            this.id = id;
            this.title = title;
            this.thumbnail = thumbnail;
            this.createdAt = createdAt;
            this.writerId = writerId;
        }
    }

    // ============================================================== Page Request

    /**
     * {@code GET /api/v1/articles} 쿼리 파라미터 ({@code @ModelAttribute} 바인딩).
     *
     * <p>설계 결정:
     * <ul>
     *   <li>{@code page} 기본 0, {@code @Min(0)}.</li>
     *   <li>{@code size} 기본 9 (요구사항: "한 페이지 9개"), {@code @Min(1)}, {@code @Max(100)}.</li>
     *   <li>{@code sort} 화이트리스트 — 현재 인덱스가 {@code created_at} 단일이므로 {@code "createdAt"}만 허용.
     *       추가 정렬축이 필요해지면 새 covering index와 함께 화이트리스트를 확장한다.</li>
     *   <li>{@code direction} 기본 DESC. {@code ASC|DESC} 만 허용 (대소문자 무관 처리는 {@link Sort.Direction#fromString}).</li>
     *   <li>{@link #toPageable()} 은 {@code id DESC} tiebreaker를 강제로 붙인다 (offset-pagination 스킬 규칙).</li>
     * </ul>
     *
     * <p>{@code @ModelAttribute} 바인딩 특성상 setter가 필요하다 — Lombok {@code @Data} 사용. 다른 도메인의
     * PageRequest 패턴과 동일.
     */
    @Data
    @NoArgsConstructor
    @Schema(description = "게시글 목록 조회 요청 (쿼리 파라미터)")
    public static class ArticlePageRequest {

        @Min(value = 0, message = "page 는 0 이상이어야 합니다.")
        @Schema(description = "페이지 번호 (0부터 시작)", defaultValue = "0", example = "0")
        private int page = 0;

        @Min(value = 1, message = "size 는 1 이상이어야 합니다.")
        @Max(value = 100, message = "size 는 100 이하여야 합니다.")
        @Schema(description = "페이지 크기 (기본 9 — 요구사항)", defaultValue = "9", example = "9")
        private int size = 9;

        // ⚠️ 화이트리스트 — 현재 인덱스가 createdAt 단일이라 createdAt 만 허용.
        @Pattern(regexp = "^(createdAt)$", message = "sort 는 'createdAt' 만 허용됩니다.")
        @Schema(description = "정렬 기준",
                defaultValue = "createdAt",
                allowableValues = {"createdAt"},
                example = "createdAt")
        private String sort = "createdAt";

        @Pattern(regexp = "^(ASC|asc|DESC|desc)$", message = "direction 은 'ASC' 또는 'DESC' 여야 합니다.")
        @Schema(description = "정렬 방향",
                defaultValue = "DESC",
                allowableValues = {"ASC", "asc", "DESC", "desc"},
                example = "DESC")
        private String direction = "DESC";

        /**
         * Spring Data {@link Pageable} 변환.
         *
         * <p>{@code id DESC} tiebreaker를 항상 보조 정렬축으로 추가한다 (covering index 컬럼 순서와
         * 일치). 이는 동률 정렬값에서 페이지 경계가 흔들리지 않게 한다 — offset-pagination 스킬 규칙 2.
         */
        public Pageable toPageable() {
            Sort.Direction d = Sort.Direction.fromString(direction.toUpperCase());
            Sort primary = Sort.by(d, sort);
            Sort tiebreaker = Sort.by(Sort.Direction.DESC, "id");
            return PageRequest.of(page, size, primary.and(tiebreaker));
        }
    }
}
