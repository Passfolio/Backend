package com.capstone.passfolio.common.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * {@link PageUtils#validatePageRange(Page)} 의 경계 검증.
 *
 * <p>설계 검증 명세 (offset-pagination 스킬 + A7):
 * <ul>
 *   <li>{@code page.getNumber() >= page.getTotalPages()} 이고 {@code totalPages > 0} 이면
 *       {@link RestException}({@link ErrorCode#PAGE_NOT_FOUND}) 를 던진다.</li>
 *   <li>{@code totalPages == 0} (빈 결과) 인 경우 {@code page=0} 이라도 정상 — 통과.</li>
 *   <li>유효 범위 내 (page < totalPages) 는 통과.</li>
 *   <li>경계 — {@code page == totalPages - 1} 는 마지막 페이지로 통과, {@code page == totalPages} 는 실패.</li>
 * </ul>
 */
class PageUtilsTest {

    @Test
    @DisplayName("정상 범위 (page < totalPages) — 예외 없이 통과")
    void inRange_doesNotThrow() {
        // totalPages = ceil(20/9) = 3. page = 0, 1, 2 모두 통과.
        Page<String> page = new PageImpl<>(List.of("a"), PageRequest.of(1, 9), 20L);

        assertThatCode(() -> PageUtils.validatePageRange(page)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("마지막 페이지 (page = totalPages - 1) 도 통과")
    void lastPage_doesNotThrow() {
        Page<String> page = new PageImpl<>(List.of("z"), PageRequest.of(2, 9), 20L);
        // totalPages = 3, page = 2 — 마지막. page < totalPages 이므로 통과.

        assertThatCode(() -> PageUtils.validatePageRange(page)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("page == totalPages — PAGE_NOT_FOUND 던짐 (사용자가 끝을 넘어감)")
    void atTotalPages_throwsPageNotFound() {
        // totalPages = 3, page = 3 — totalPages 와 같으면 범위 밖.
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(3, 9), 20L);

        assertThatThrownBy(() -> PageUtils.validatePageRange(page))
                .isInstanceOf(RestException.class)
                .satisfies(e -> {
                    RestException re = (RestException) e;
                    org.assertj.core.api.Assertions.assertThat(re.getErrorCode())
                            .isEqualTo(ErrorCode.PAGE_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("page > totalPages 도 실패 — 예: page=999, totalPages=3")
    void wayPastEnd_throwsPageNotFound() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(999, 9), 20L);

        assertThatThrownBy(() -> PageUtils.validatePageRange(page))
                .isInstanceOf(RestException.class);
    }

    @Test
    @DisplayName("빈 결과 (totalPages=0) + page=0 — 정상 (조건 거짓)")
    void emptyResult_page0_passes() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 9), 0L);

        assertThatCode(() -> PageUtils.validatePageRange(page)).doesNotThrowAnyException();
    }
}
