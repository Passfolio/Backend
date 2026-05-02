package com.capstone.passfolio.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.Optional;

import com.capstone.passfolio.domain.article.service.ArticleService;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import com.capstone.passfolio.system.security.model.UserPrincipal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link UserService} 단위 테스트 — T14 의 {@code deleteAdmin} 진입점에 한정.
 *
 * <p>검증 명세 (acceptance signal A10 + design §"Decisions: Admin user delete = bulk article + bulk S3"):
 * <ul>
 *   <li>RBAC: {@code caller == null} 또는 USER 면 {@code AUTH_FORBIDDEN}.</li>
 *   <li>self-delete 방지: caller 가 자기 자신을 삭제 → {@code GLOBAL_INVALID_PARAMETER}.</li>
 *   <li>대상 미존재 → {@code USER_NOT_FOUND}.</li>
 *   <li>대상이 USER (ADMIN 이 아닌 경우) → {@code GLOBAL_INVALID_PARAMETER}.</li>
 *   <li>정상 흐름: {@code articleService.deleteAllByWriter(targetId)} 가 user row delete 보다 먼저 호출
 *       (FK 무결성 + audit trail 순서 보장).</li>
 *   <li>{@code targetUserId == null} → {@code GLOBAL_INVALID_PARAMETER}.</li>
 * </ul>
 *
 * <p>{@code retrieveMe} 같은 기존 메서드는 본 백로그 변경 범위 밖이라 테스트하지 않는다 (단위-테스트 스코프
 * = 신규 surface 만).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock ArticleService articleService;

    @InjectMocks UserService userService;

    private static UserPrincipal admin(Long id) {
        return UserPrincipal.builder().userId(id).username("admin" + id).role(Role.ADMIN).build();
    }

    private static UserPrincipal user(Long id) {
        return UserPrincipal.builder().userId(id).username("user" + id).role(Role.USER).build();
    }

    private static User persistedUser(Long id, Role role) {
        User u = User.builder().nickname("n").username("u").profileImageUrl("p").role(role).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Nested
    @DisplayName("deleteAdmin(): RBAC 게이트")
    class Rbac {

        @Test
        @DisplayName("caller == null → AUTH_FORBIDDEN, 어떤 작업도 수행되지 않음")
        void nullCaller_forbidden() {
            assertThatThrownBy(() -> userService.deleteAdmin(2L, null))
                    .isInstanceOf(RestException.class)
                    .satisfies(e -> assertThat(((RestException) e).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_FORBIDDEN));

            then(userRepository).shouldHaveNoInteractions();
            then(articleService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("caller 가 USER → AUTH_FORBIDDEN")
        void userCaller_forbidden() {
            assertThatThrownBy(() -> userService.deleteAdmin(2L, user(1L)))
                    .isInstanceOf(RestException.class)
                    .satisfies(e -> assertThat(((RestException) e).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_FORBIDDEN));

            then(userRepository).shouldHaveNoInteractions();
            then(articleService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("caller role 이 null → AUTH_FORBIDDEN (방어적 가드)")
        void nullRole_forbidden() {
            UserPrincipal noRole = UserPrincipal.builder().userId(1L).username("x").role(null).build();
            assertThatThrownBy(() -> userService.deleteAdmin(2L, noRole))
                    .isInstanceOf(RestException.class);

            then(userRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("deleteAdmin(): 입력 가드")
    class InputGuard {

        @Test
        @DisplayName("targetUserId == null → GLOBAL_INVALID_PARAMETER")
        void nullTarget_invalidParameter() {
            assertThatThrownBy(() -> userService.deleteAdmin(null, admin(1L)))
                    .isInstanceOf(RestException.class)
                    .satisfies(e -> assertThat(((RestException) e).getErrorCode())
                            .isEqualTo(ErrorCode.GLOBAL_INVALID_PARAMETER));

            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("self-delete (caller == targetUserId) → GLOBAL_INVALID_PARAMETER")
        void selfDelete_blocked() {
            assertThatThrownBy(() -> userService.deleteAdmin(1L, admin(1L)))
                    .isInstanceOf(RestException.class)
                    .satisfies(e -> assertThat(((RestException) e).getErrorCode())
                            .isEqualTo(ErrorCode.GLOBAL_INVALID_PARAMETER));

            then(userRepository).should(never()).findById(anyLong());
            then(articleService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("deleteAdmin(): 대상 검증")
    class TargetValidation {

        @Test
        @DisplayName("대상 user 미존재 → USER_NOT_FOUND")
        void targetNotFound_throws() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteAdmin(99L, admin(1L)))
                    .isInstanceOf(RestException.class)
                    .satisfies(e -> assertThat(((RestException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));

            then(articleService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("대상이 USER → GLOBAL_INVALID_PARAMETER (이 진입점은 ADMIN 삭제만 담당)")
        void targetIsUser_invalidParameter() {
            given(userRepository.findById(2L)).willReturn(Optional.of(persistedUser(2L, Role.USER)));

            assertThatThrownBy(() -> userService.deleteAdmin(2L, admin(1L)))
                    .isInstanceOf(RestException.class)
                    .satisfies(e -> assertThat(((RestException) e).getErrorCode())
                            .isEqualTo(ErrorCode.GLOBAL_INVALID_PARAMETER));

            then(articleService).shouldHaveNoInteractions();
            then(userRepository).should(never()).deleteByUserId(anyLong());
        }
    }

    @Nested
    @DisplayName("deleteAdmin(): 정상 흐름 — Article 정리 → User row 삭제")
    class HappyPath {

        @Test
        @DisplayName("Article bulk delete 가 user row delete 보다 먼저 호출된다 (FK + audit 순서)")
        void articleDeleteBeforeUserDelete() {
            given(userRepository.findById(2L)).willReturn(Optional.of(persistedUser(2L, Role.ADMIN)));

            userService.deleteAdmin(2L, admin(1L));

            InOrder inOrder = Mockito.inOrder(articleService, userRepository);
            inOrder.verify(articleService).deleteAllByWriter(2L);
            inOrder.verify(userRepository).deleteByUserId(2L);

            then(articleService).should(times(1)).deleteAllByWriter(2L);
            then(userRepository).should(times(1)).deleteByUserId(2L);
        }

        @Test
        @DisplayName("ArticleService.deleteAllByWriter 에 정확히 targetUserId 가 전달된다")
        void targetIdPassedToArticleService() {
            given(userRepository.findById(42L)).willReturn(Optional.of(persistedUser(42L, Role.ADMIN)));

            userService.deleteAdmin(42L, admin(1L));

            then(articleService).should(times(1)).deleteAllByWriter(42L);
        }
    }
}
