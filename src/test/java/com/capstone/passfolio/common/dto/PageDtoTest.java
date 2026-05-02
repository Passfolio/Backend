package com.capstone.passfolio.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * {@link PageDto} 의 {@link PageDto.PageInfo}/{@link PageDto.PageListResponse} 동작 검증.
 *
 * <p>설계 검증 명세:
 * <ul>
 *   <li>{@code PageInfo.of(Page)} — Spring Data {@link Page} 메타데이터를 6개 필드 (page, size,
 *       totalElements, totalPages, hasNext, hasPrev) 로 정확히 매핑.</li>
 *   <li>{@code PageListResponse.of(title, Page)} — title + PageInfo + page.getContent() 결합.</li>
 *   <li>경계: 빈 결과 (totalPages=0), 단일 페이지 (hasNext=false, hasPrev=false), 중간 페이지
 *       (양쪽 true), 마지막 페이지 (hasNext=false, hasPrev=true).</li>
 * </ul>
 */
class PageDtoTest {

    @Nested
    @DisplayName("PageInfo.of(): Spring Data Page 메타데이터 매핑")
    class PageInfoOf {

        @Test
        @DisplayName("중간 페이지 — page=1, size=9, totalElements=20, hasNext=true, hasPrev=true")
        void middlePage_metaCorrect() {
            // 20개 항목 중 9개 = page 1 (2번째 페이지). totalPages = ceil(20/9) = 3.
            Page<String> page = new PageImpl<>(
                    List.of("a", "b", "c", "d", "e", "f", "g", "h", "i"),
                    PageRequest.of(1, 9),
                    20L);

            PageDto.PageInfo info = PageDto.PageInfo.of(page);

            assertThat(info.getPage()).isEqualTo(1);
            assertThat(info.getSize()).isEqualTo(9);
            assertThat(info.getTotalElements()).isEqualTo(20L);
            assertThat(info.getTotalPages()).isEqualTo(3);
            assertThat(info.isHasNext()).isTrue();
            assertThat(info.isHasPrev()).isTrue();
        }

        @Test
        @DisplayName("첫 페이지 — hasPrev=false, hasNext=true")
        void firstPage_hasPrevFalse() {
            Page<String> page = new PageImpl<>(
                    List.of("a", "b", "c"),
                    PageRequest.of(0, 3),
                    10L);

            PageDto.PageInfo info = PageDto.PageInfo.of(page);

            assertThat(info.isHasPrev()).isFalse();
            assertThat(info.isHasNext()).isTrue();
            assertThat(info.getPage()).isEqualTo(0);
        }

        @Test
        @DisplayName("마지막 페이지 — hasNext=false, hasPrev=true")
        void lastPage_hasNextFalse() {
            // totalPages = ceil(10/3) = 4. 마지막 페이지 index = 3. 마지막은 항목 1개.
            Page<String> page = new PageImpl<>(
                    List.of("j"),
                    PageRequest.of(3, 3),
                    10L);

            PageDto.PageInfo info = PageDto.PageInfo.of(page);

            assertThat(info.isHasNext()).isFalse();
            assertThat(info.isHasPrev()).isTrue();
            assertThat(info.getPage()).isEqualTo(3);
            assertThat(info.getTotalPages()).isEqualTo(4);
        }

        @Test
        @DisplayName("빈 결과 — totalPages=0, totalElements=0, hasNext/hasPrev=false")
        void emptyResult_zeroPages() {
            Page<String> page = new PageImpl<>(
                    List.of(),
                    PageRequest.of(0, 9),
                    0L);

            PageDto.PageInfo info = PageDto.PageInfo.of(page);

            assertThat(info.getTotalElements()).isEqualTo(0L);
            assertThat(info.getTotalPages()).isEqualTo(0);
            assertThat(info.isHasNext()).isFalse();
            assertThat(info.isHasPrev()).isFalse();
        }

        @Test
        @DisplayName("단일 페이지 — hasNext=false, hasPrev=false")
        void singlePage_bothFalse() {
            Page<String> page = new PageImpl<>(
                    List.of("a", "b"),
                    PageRequest.of(0, 9),
                    2L);

            PageDto.PageInfo info = PageDto.PageInfo.of(page);

            assertThat(info.isHasNext()).isFalse();
            assertThat(info.isHasPrev()).isFalse();
            assertThat(info.getTotalPages()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("PageListResponse.of(): title + 메타 + content 결합")
    class PageListResponseOf {

        @Test
        @DisplayName("title 과 page.getContent() 가 그대로 직렬화되며 PageInfo 메타가 포함된다")
        void wrapsTitleMetaContent() {
            List<String> content = List.of("a", "b", "c");
            Page<String> page = new PageImpl<>(content, PageRequest.of(0, 3), 9L);

            PageDto.PageListResponse<String> resp = PageDto.PageListResponse.of("아티클 목록", page);

            assertThat(resp.getTitle()).isEqualTo("아티클 목록");
            assertThat(resp.getContent()).containsExactly("a", "b", "c");
            // page.getContent() 와 동일한 인스턴스 (또는 동등) — 추가 변환 없음.
            assertThat(resp.getContent()).isEqualTo(content);

            assertThat(resp.getPage()).isNotNull();
            assertThat(resp.getPage().getPage()).isEqualTo(0);
            assertThat(resp.getPage().getSize()).isEqualTo(3);
            assertThat(resp.getPage().getTotalElements()).isEqualTo(9L);
            assertThat(resp.getPage().getTotalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("빈 page 도 정상 래핑 — content 는 비어있는 List")
        void emptyPage_wrappedAsEmptyContent() {
            Page<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 9), 0L);

            PageDto.PageListResponse<String> resp = PageDto.PageListResponse.of("빈 목록", page);

            assertThat(resp.getTitle()).isEqualTo("빈 목록");
            assertThat(resp.getContent()).isEmpty();
            assertThat(resp.getPage().getTotalElements()).isEqualTo(0L);
            assertThat(resp.getPage().getTotalPages()).isEqualTo(0);
        }
    }
}
