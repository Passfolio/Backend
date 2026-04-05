package com.capstone.passfolio.system.security.jwt.dto;

import com.capstone.passfolio.domain.user.entity.enums.Role;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class JwtDto {
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class TokenData {
        private String token; // returned by Jwts.buidler()
        private LocalDateTime expiredAt;
        private String jti;
    }

    @Builder @AllArgsConstructor @NoArgsConstructor @Getter
    public static class TokenPair {
        private JwtDto.TokenData refreshToken;
        private JwtDto.TokenData accessToken;

        public static TokenPair of(JwtDto.TokenData refreshToken, JwtDto.TokenData accessToken) {
            return TokenPair.builder()
                    .refreshToken(refreshToken)
                    .accessToken(accessToken)
                    .build();
        }
    }

    @Builder @AllArgsConstructor @NoArgsConstructor @Getter
    public static class TokenStringPair {
        private String accessToken;
        private String refreshToken;

        public static TokenStringPair of (String accessToken, String refreshToken) {
            return TokenStringPair.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .build();
        }
    }

    @Builder @AllArgsConstructor @NoArgsConstructor @Getter
    public static class TokenPayload {
        private LocalDateTime expiredAt;
        private String subject;
        private Role role;
        private TokenType tokenType;
        private String refreshUuid;
        private String jti;
        private Boolean rememberMe; // RTK에 포함된 자동 로그인 여부
    }

    @Builder @AllArgsConstructor @NoArgsConstructor @Getter
    @Schema(description = "토큰 정보 발행 DTO")
    public static class TokenInfo {
        @Schema(description = "Access Token", example = "accessTokenContent")
        private String accessToken;
        @Schema(description = "Refresh Token", example = "refreshTokenContent")
        private String refreshToken;
        @Schema(description = "Access Token 만료 시간", example = "KST DateTime")
        private LocalDateTime accessTokenExpiresAt;
        @Schema(description = "Refresh Token 만료 시간", example = "KST DateTime")
        private LocalDateTime refreshTokenExpiresAt;

        public static TokenInfo from(JwtDto.TokenPair tokenPair) {
            return TokenInfo.builder()
                    .accessToken(tokenPair.getAccessToken().getToken())
                    .refreshToken(tokenPair.getRefreshToken().getToken())
                    .accessTokenExpiresAt(tokenPair.getAccessToken().getExpiredAt())
                    .refreshTokenExpiresAt(tokenPair.getRefreshToken().getExpiredAt())
                    .build();
        }
    }

    @Builder @AllArgsConstructor @NoArgsConstructor @Getter
    @Schema(description = "토큰 만료시간 정보 발행 DTO")
    public static class TokenExpiresInfo {
        @Schema(description = "Access Token 만료 시간", example = "KST DateTime")
        private LocalDateTime accessTokenExpiresAt;
        @Schema(description = "Refresh Token 만료 시간", example = "KST DateTime")
        private LocalDateTime refreshTokenExpiresAt;

        public static TokenExpiresInfo from(JwtDto.TokenInfo tokenInfo) {
            return TokenExpiresInfo.builder()
                    .accessTokenExpiresAt(tokenInfo.getAccessTokenExpiresAt())
                    .refreshTokenExpiresAt(tokenInfo.getRefreshTokenExpiresAt())
                    .build();
        }

        public static TokenExpiresInfo of(JwtDto.TokenPayload atkPayload, JwtDto.TokenPayload rtkPayload) {
            return TokenExpiresInfo.builder()
                    .accessTokenExpiresAt(atkPayload.getExpiredAt())
                    .refreshTokenExpiresAt(rtkPayload.getExpiredAt())
                    .build();
        }
    }

    // 어떤 요구사항의 변동이 와도 유연하게 토큰처리하기 하기 위한 Wrapper DTO
    @Builder @AllArgsConstructor @NoArgsConstructor @Getter
    public static class TokenOptionWrapper {
        private UserPrincipal userPrincipal;
        private boolean rememberMe;
        private HttpServletRequest httpServletRequest;
        private HttpServletResponse httpServletResponse;

        private JwtDto.TokenPayload atkPayload;
        private JwtDto.TokenPayload rtkPayload;

        private String allowedRtkUuid;
        private String subject;

        // 만약 어떤 패러미터가 추가되어야 한다고 해도, 여기에 필드만 처리하면 레거시 상태여도 유연히 대응 가능

        public static JwtDto.TokenOptionWrapper of(UserPrincipal userPrincipal, boolean rememberMe) {
            return JwtDto.TokenOptionWrapper.builder()
                    .userPrincipal(userPrincipal)
                    .rememberMe(rememberMe)
                    .build();
        }

        public static JwtDto.TokenOptionWrapper of(
                HttpServletRequest httpServletRequest,
                HttpServletResponse httpServletResponse,
                UserPrincipal userPrincipal,
                boolean rememberMe) {

            return TokenOptionWrapper.builder()
                    .httpServletRequest(httpServletRequest)
                    .httpServletResponse(httpServletResponse)
                    .userPrincipal(userPrincipal)
                    .rememberMe(rememberMe)
                    .build();
        }

        public static JwtDto.TokenOptionWrapper of(
                HttpServletRequest httpServletRequest,
                HttpServletResponse httpServletResponse,
                boolean rememberMe) {

            return TokenOptionWrapper.builder()
                    .httpServletRequest(httpServletRequest)
                    .httpServletResponse(httpServletResponse)
                    .rememberMe(rememberMe)
                    .build();
        }

        public static JwtDto.TokenOptionWrapper of(
            JwtDto.TokenPayload atkPayload,
            JwtDto.TokenPayload rtkPayload
        ) {
            return TokenOptionWrapper.builder()
                    .atkPayload(atkPayload)
                    .rtkPayload(rtkPayload)
                    .build();
        }
    }
}
