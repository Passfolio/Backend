package com.capstone.passfolio.system.security.jwt.util;

import com.capstone.passfolio.system.security.jwt.dto.JwtDto;
import com.capstone.passfolio.system.security.jwt.dto.TokenType;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final SecretKey secretKey;

    @Value("${jwt.atk-exp-min}")
    private int accessTokenExpirationMinutes;

    @Value("${jwt.rtk-exp-week}")
    private int refreshTokenExpirationWeeks;

    public JwtDto.TokenData createRefreshToken(JwtDto.TokenOptionWrapper tokenOptions, String refreshUuid) {
        String jti = UUID.randomUUID().toString();

        // 자동 로그인이면 RTK를 3개월로 상정한다.
        int rtkExpWeeks = tokenOptions.isRememberMe() ? 12 : refreshTokenExpirationWeeks;

        LocalDateTime exp = LocalDateTime.now().plusWeeks(rtkExpWeeks);

        String token = Jwts.builder()
                .subject(getSubject(tokenOptions.getUserPrincipal()))
                .claim("refreshUuid", refreshUuid)
                .claim("type", TokenType.REFRESH.name())
                .claim("rememberMe", tokenOptions.isRememberMe()) // 자동 로그인 여부를 RTK Claims에 포함
                .id(jti)
                .issuedAt(new Date())
                .expiration(Date.from(exp.atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(secretKey)
                .compact();

        return JwtDto.TokenData.builder()
                .token(token)
                .expiredAt(exp)
                .jti(jti)
                .build();
    }

    public JwtDto.TokenData createAccessToken(JwtDto.TokenOptionWrapper tokenOption, String refreshUuid) {
        String jti = UUID.randomUUID().toString();
        LocalDateTime exp = LocalDateTime.now().plusMinutes(accessTokenExpirationMinutes);

        String token = Jwts.builder()
                .subject(getSubject(tokenOption.getUserPrincipal()))
                .claim("role", tokenOption.getUserPrincipal().getRole().name())
                .claim("refreshUuid", refreshUuid)
                .claim("type", TokenType.ACCESS.name())
                .id(jti)
                .issuedAt(new Date())
                .expiration(Date.from(exp.atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(secretKey)
                .compact();

        return JwtDto.TokenData.builder()
                .token(token)
                .expiredAt(exp)
                .jti(jti)
                .build();
    }

    public JwtDto.TokenPair createTokenPair(JwtDto.TokenOptionWrapper tokenOption) {
        String refreshUuid = UUID.randomUUID().toString();
        JwtDto.TokenData accessToken = createAccessToken(tokenOption, refreshUuid);
        JwtDto.TokenData refreshToken = createRefreshToken(tokenOption, refreshUuid);

        return JwtDto.TokenPair.of(refreshToken, accessToken);
    }

    /**
     * 기존 RTK UUID를 재사용하여 새 토큰 페어 생성
     * Race Condition 패배자를 위한 Graceful Fallback에서 사용
     * @param tokenOption 토큰 생성 옵션
     * @param refreshUuid 재사용할 RTK UUID (Redis에 이미 등록된 UUID)
     * @return 새 토큰 페어 (동일한 refreshUuid로 생성됨)
     */
    public JwtDto.TokenPair createTokenPairWithUuid(
            JwtDto.TokenOptionWrapper tokenOption,
            String refreshUuid
    ) {
        JwtDto.TokenData accessToken = createAccessToken(tokenOption, refreshUuid);
        JwtDto.TokenData refreshToken = createRefreshToken(tokenOption, refreshUuid);

        return JwtDto.TokenPair.of(refreshToken, accessToken);
    }

    private String getSubject(UserPrincipal userPrincipal) {
        // OAuth2 사용자의 경우 userId가 null이므로 username을 subject로 사용
        return userPrincipal.getUserId() != null
                ? userPrincipal.getUserId().toString()
                : userPrincipal.getUsername();
    }
}
