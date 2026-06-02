package com.capstone.passfolio.domain.user.dto;

import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
public class UserDto {
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "사용자 정보 응답 DTO")
    public static class UserResponse {
        @Schema(description = "PK", example = "1")
        private Long id;
        @Schema(description = "사용자 역할", example = "USER | ADMIN")
        private Role role;
        @Schema(description = "사용자 닉네임", example = "hades")
        private String nickname;
        @Schema(description = "프로필 이미지 URL", example = "https://avatars.githubusercontent.com/u/12345")
        private String profileImageUrl;
        @Schema(description = "GitHub Login Name", example = "Youcu")
        private String githubLogin;
        @Schema(description = "계정 생성 시간", example = "KST Datetime")
        private LocalDateTime createdAt;
        @Schema(description = "마지막 정보 수정 시간", example = "KST Datetime")
        private LocalDateTime lastModifiedAt;

        public static UserResponse from(User user) {
            return UserResponse.builder()
                    .id(user.getId())
                    .role(user.getRole())
                    .nickname(user.getNickname())
                    .profileImageUrl(user.getProfileImageUrl())
                    .githubLogin(user.getGithubLogin())
                    .createdAt(user.getCreatedAt())
                    .lastModifiedAt(LocalDateTime.now())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "날짜별 가입자 수(사용자 유입 그래프용)")
    public static class DailySignupResponse {
        @Schema(description = "가입 날짜(yyyy-MM-dd)", example = "2026-06-01")
        private String date;
        @Schema(description = "해당 날짜 신규 가입자 수", example = "5")
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "회원 목록 집계 항목(우선 id·nickname만 — QueryDSL/커버링 인덱스 최적화는 추후)")
    public static class UserSummaryResponse {
        @Schema(description = "사용자 PK", example = "1")
        private Long userId;
        @Schema(description = "사용자 닉네임", example = "hades")
        private String nickname;

        public static UserSummaryResponse from(User user) {
            return UserSummaryResponse.builder()
                    .userId(user.getId())
                    .nickname(user.getNickname())
                    .build();
        }
    }
}