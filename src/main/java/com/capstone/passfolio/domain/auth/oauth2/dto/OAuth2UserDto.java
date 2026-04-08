package com.capstone.passfolio.domain.auth.oauth2.dto;

import com.capstone.passfolio.system.util.HmacUtil;
import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder @NoArgsConstructor @AllArgsConstructor @Data
public class OAuth2UserDto {
    private Long userId;
    private Role role;
    private String nickname;
    private String username;
    private String profileImageUrl;

    public static OAuth2UserDto of(Role role, OAuth2Response oAuth2Response, HmacUtil hmacUtil) {
        return OAuth2UserDto.builder()
                .userId(null) // OAuth2Response 엔 userId가 없음 -> user 등록 하고 해당 DTO update 해야 함.
                .role(role)
                .nickname(suggestNickname(oAuth2Response, hmacUtil))
                .username(generateUsername(oAuth2Response, hmacUtil))
                .profileImageUrl(oAuth2Response.getProfileImageUrl())
                .build();
    }

    public static OAuth2UserDto from(User user) {
        return OAuth2UserDto.builder()
                .userId(user.getId()) // user 등록된 이후 DTO Update 됨
                .role(user.getRole())
                .nickname(user.getNickname())
                .username(user.getUsername())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }

    public User toUser(Long githubId, String githubLogin) {
        return User.builder()
                .role(this.role)
                .nickname(this.nickname)
                .username(this.username)
                .profileImageUrl(this.profileImageUrl)
                .githubId(githubId)
                .githubLogin(githubLogin)
                .build();
    }

    private static String generateUsername(OAuth2Response oAuth2Response, HmacUtil hmacUtil) {
        String hashedId = hmacUtil.hmacSha256Base64(oAuth2Response.getProviderId());
        return oAuth2Response.getProvider() + "_" + hashedId;
    }

    private static String suggestNickname(OAuth2Response oAuth2Response, HmacUtil hmacUtil) {
        String nickname = oAuth2Response.getNickname(); // 닉네임 null/blank 시 대체.
        if (!nickname.isEmpty()) return nickname;
        String hashedId = hmacUtil.hmacSha256Base64(oAuth2Response.getProviderId());

        return "user_" + oAuth2Response.getProvider() + "_" + hashedId;
    }
}
