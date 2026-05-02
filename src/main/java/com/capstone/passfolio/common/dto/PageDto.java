package com.capstone.passfolio.common.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 오프셋 페이지네이션 공용 응답 래퍼.
 *
 * <p>설계 결정:
 * <ul>
 *   <li>{@link PageInfo} — 클라이언트가 페이지 UI를 렌더링하는 데 필요한 메타데이터.</li>
 *   <li>{@link PageListResponse} — 페이지 제목(title) + 메타데이터 + 콘텐츠 리스트를
 *       하나의 응답 바디로 묶는 래퍼. 모든 페이지네이션 API가 이 타입을 반환한다.</li>
 *   <li>{@code of(String, Page)} 팩토리: {@link org.springframework.data.domain.Page}를
 *       직접 받아 메타데이터를 추출하므로 호출부에 보일러플레이트가 없다.</li>
 * </ul>
 *
 * <p>사용 예:
 * <pre>{@code
 * Page<ArticleDto.ArticlePageResponse> page = articleQueryRepository.searchPage(pageable);
 * PageUtils.validatePageRange(page);
 * return PageDto.PageListResponse.of("아티클 목록", page);
 * }</pre>
 */
public class PageDto {

    /**
     * 페이지 메타데이터.
     */
    @Getter
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PageInfo {

        /** 현재 페이지 번호 (0-based). */
        private int page;

        /** 페이지 당 항목 수. */
        private int size;

        /** 전체 항목 수. */
        private long totalElements;

        /** 전체 페이지 수. */
        private int totalPages;

        /** 다음 페이지 존재 여부. */
        private boolean hasNext;

        /** 이전 페이지 존재 여부. */
        private boolean hasPrev;

        /**
         * {@link Page}로부터 {@link PageInfo}를 생성한다.
         *
         * @param page Spring Data Page 객체.
         * @return 메타데이터가 채워진 {@link PageInfo} 인스턴스.
         */
        public static PageInfo of(Page<?> page) {
            return new PageInfo(
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.hasNext(),
                    page.hasPrevious()
            );
        }
    }

    /**
     * 페이지네이션 API 공용 응답 래퍼.
     *
     * @param <T> 콘텐츠 항목 타입.
     */
    @Getter
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PageListResponse<T> {

        /** 페이지 제목 (예: "아티클 목록"). */
        private String title;

        /** 페이지 메타데이터. */
        private PageInfo page;

        /** 현재 페이지의 콘텐츠 목록. */
        private List<T> content;

        /**
         * {@link Page}로부터 {@link PageListResponse}를 생성한다.
         *
         * @param title   클라이언트에 노출될 페이지 제목.
         * @param page    Spring Data Page 객체.
         * @param <T>     콘텐츠 항목 타입.
         * @return 제목·메타데이터·콘텐츠가 채워진 {@link PageListResponse} 인스턴스.
         */
        public static <T> PageListResponse<T> of(String title, Page<T> page) {
            return new PageListResponse<>(
                    title,
                    PageInfo.of(page),
                    page.getContent()
            );
        }
    }
}
