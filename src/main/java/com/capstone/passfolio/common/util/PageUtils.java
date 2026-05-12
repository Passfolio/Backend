package com.capstone.passfolio.common.util;

import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

/**
 * 오프셋 페이지네이션 공용 유틸리티.
 *
 * <p>설계 결정:
 * <ul>
 *   <li>{@link #validatePageRange(Page)} — 요청된 페이지 번호가 실제 총 페이지 수를 초과할 경우
 *       {@link ErrorCode#PAGE_NOT_FOUND}를 던진다. totalPages = 0 (빈 목록)인 경우는 정상으로 허용.
 *       오프셋 페이지네이션 스킬 요구사항: "user navigated past end" 시 404 반환.</li>
 *   <li>정적 유틸 클래스이므로 인스턴스화를 금지한다 ({@code @NoArgsConstructor(PRIVATE)}).</li>
 * </ul>
 *
 * <p>사용 예:
 * <pre>{@code
 * Page<ArticleDto.ArticlePageResponse> page = articleQueryRepository.searchPage(pageable);
 * PageUtils.validatePageRange(page);
 * return PageDto.PageListResponse.of("아티클 목록", page);
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageUtils {

    /**
     * 요청된 페이지 범위를 검증한다.
     *
     * <p>조건:
     * <ul>
     *   <li>{@code page.getTotalPages() > 0} 이고</li>
     *   <li>{@code page.getNumber() >= page.getTotalPages()}</li>
     * </ul>
     * 인 경우 {@link RestException}({@link ErrorCode#PAGE_NOT_FOUND})을 던진다.
     *
     * <p>빈 결과 ({@code totalPages == 0})는 정상으로 허용한다. 이 경우
     * {@code page.getNumber() == 0}이므로 조건 자체가 거짓이 된다.
     *
     * @param page Spring Data {@link Page} 객체. {@code null} 비허용.
     * @throws RestException {@link ErrorCode#PAGE_NOT_FOUND} — 페이지 번호가 범위를 초과할 때.
     */
    public static void validatePageRange(Page<?> page) {
        if (page.getTotalPages() > 0 && page.getNumber() >= page.getTotalPages()) {
            throw new RestException(ErrorCode.PAGE_NOT_FOUND);
        }
    }
}
