package com.capstone.passfolio.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstone.passfolio.domain.user.dto.UserSoftDeleteDto;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("User.softDelete + UserSoftDeleteDto — 익명화")
class UserSoftDeleteTest {

    private static User sampleUser() {
        return User.builder()
                .id(7L)
                .username("github_abc123")
                .nickname("Hooby")
                .profileImageUrl("https://img/avatar.png")
                .role(Role.USER)
                .githubId(99L)
                .githubLogin("Youcu")
                .password("encodedPw")
                .build();
    }

    @Test
    @DisplayName("softDelete 후 unique/PII 익명화 + github_id/login/password null")
    void softDelete_anonymizes_unique_and_pii() {
        User user = sampleUser();

        user.softDelete(UserSoftDeleteDto.of(user));

        assertThat(user.getUsername()).startsWith("deleted_").isNotEqualTo("github_abc123");
        assertThat(user.getNickname()).isEqualTo("(탈퇴한 사용자)");
        assertThat(user.getProfileImageUrl()).isEqualTo("deleted");
        assertThat(user.getGithubId()).isNull();
        assertThat(user.getGithubLogin()).isNull();
        assertThat(user.getPassword()).isNull();
    }

    @Test
    @DisplayName("SoftDeleteDto.of — user id가 섞여 결정적·고유(서로 다른 user는 다른 username)")
    void dto_of_is_deterministic_and_unique() {
        User u1 = User.builder().id(1L).username("same").nickname("a").profileImageUrl("x").role(Role.USER).build();
        User u2 = User.builder().id(2L).username("same").nickname("b").profileImageUrl("y").role(Role.USER).build();

        String a1 = UserSoftDeleteDto.of(u1).getUsername();
        String a1again = UserSoftDeleteDto.of(u1).getUsername();
        String a2 = UserSoftDeleteDto.of(u2).getUsername();

        assertThat(a1).isEqualTo(a1again);   // 결정적
        assertThat(a1).isNotEqualTo(a2);     // user 별 고유(unique 충돌 방지)
    }

    @Test
    @DisplayName("softDelete(null) — no-op (방어적)")
    void softDelete_null_is_noop() {
        User user = sampleUser();
        user.softDelete(null);
        assertThat(user.getUsername()).isEqualTo("github_abc123");
        assertThat(user.getGithubId()).isEqualTo(99L);
    }
}
