package com.capstone.passfolio.domain.user.dto;

import com.capstone.passfolio.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * USER 회원탈퇴 시 Soft Delete 익명화 값 묶음.
 *
 * <p>참조: DEPth/BE {@code AuthDto.SoftDeleteDto}. {@code deletedAt} 플래그 대신 unique/PII 필드를
 * 익명화해 행은 남기되 식별 불가하게 만든다. unique 컬럼({@code username})은 user id 기반으로
 * 결정적·고유하게 변환해 재가입 시 충돌을 막고, 익명화된 자격증명으로는 로그인이 불가하다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSoftDeleteDto {
    private String username;
    private String nickname;
    private String profileImageUrl;

    public static UserSoftDeleteDto of(User user) {
        return UserSoftDeleteDto.builder()
                .username(anonymize(user.getId().toString(), user.getUsername()))
                .nickname("(탈퇴한 사용자)")
                .profileImageUrl("deleted")
                .build();
    }

    /** user id를 섞어 결정적·고유한 익명 식별자 생성(unique 충돌 방지). */
    private static String anonymize(String idPart, String original) {
        String combined = idPart + ":" + (original == null ? "" : original);
        return "deleted_" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(combined.getBytes(StandardCharsets.UTF_8));
    }
}
