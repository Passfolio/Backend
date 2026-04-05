package com.capstone.passfolio.system.security.jwt.util;

import com.capstone.passfolio.system.security.jwt.dto.JwtDto;
import com.capstone.passfolio.system.security.jwt.dto.TokenType;
import com.capstone.passfolio.system.security.jwt.exception.JwtBlacklistException;
import com.capstone.passfolio.system.security.jwt.exception.JwtExpiredException;
import com.capstone.passfolio.system.security.jwt.exception.JwtInvalidException;
import com.capstone.passfolio.system.security.jwt.exception.JwtMalformedException;
import com.capstone.passfolio.system.security.jwt.repository.TokenRedisRepository;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;

@Slf4j
@RequiredArgsConstructor
public class JwtTokenValidator {
    private final TokenRedisRepository tokenRedisRepository;
    private final SecretKey secretKey;

    public void validateRtk(JwtDto.TokenPayload payload) {
        if (payload.getTokenType() != TokenType.REFRESH) {
            log.warn("⚠️ RTK validation failed: TokenType이 REFRESH가 아님 - {}", payload.getTokenType());
            throw new JwtInvalidException();
        }
        if (payload.getSubject() == null || payload.getSubject().isEmpty()) {
            log.warn("⚠️ RTK validation failed: Subject가 null이거나 비어있음");
            throw new JwtInvalidException();
        }
        if (payload.getRefreshUuid() == null || payload.getRefreshUuid().isEmpty()) {
            log.warn("⚠️ RTK validation failed: RefreshUuid가 null이거나 비어있음");
            throw new JwtInvalidException();
        }

        String submittedUuid = payload.getRefreshUuid();
        String allowedRtk = tokenRedisRepository.getAllowedRtk(payload.getSubject());

        if (allowedRtk == null) {
            log.warn("⚠️ RTK validation failed: Redis에 허용된 RTK가 없음 - Subject: {}", payload.getSubject());
            throw new JwtInvalidException();
        }
        if (!allowedRtk.equals(submittedUuid)) {
            log.warn("⚠️ RTK validation failed: RTK UUID 불일치 - 제출된 UUID: {}, 허용된 UUID: {}", 
                submittedUuid, allowedRtk);
            throw new JwtInvalidException();
        }
        if (tokenRedisRepository.isRtkBlacklisted(submittedUuid)) {
            log.warn("⚠️ RTK validation failed: 블랙리스트에 등록된 토큰 - UUID: {}", submittedUuid);
            throw new JwtBlacklistException();
        }
    }

    public void validateAtk(JwtDto.TokenPayload payload) {
        if (payload.getTokenType() != TokenType.ACCESS) throw new JwtInvalidException();
        if (payload.getJti() == null) throw new JwtInvalidException();
        if (tokenRedisRepository.isAtkBlacklisted(payload.getJti())) throw new JwtBlacklistException();
    }

    public Jws<Claims> parseClaimsWithValidation(String token) {
        try {
            return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
        } catch (SecurityException e) {
            log.error("⚠️ JWT SecurityException - 시크릿 키 불일치 가능성: {}", e.getMessage());
            throw new JwtInvalidException(e);
        } catch (UnsupportedJwtException e) {
            log.error("⚠️ JWT UnsupportedJwtException: {}", e.getMessage());
            throw new JwtInvalidException(e);
        } catch (IllegalArgumentException e) {
            log.error("⚠️ JWT IllegalArgumentException: {}", e.getMessage());
            throw new JwtInvalidException(e);
        } catch (MalformedJwtException e) {
            throw new JwtMalformedException(e);
        } catch (ExpiredJwtException e) {
            throw new JwtExpiredException(e);
        }
    }

    public Claims parseExpiredTokenClaims(String token) {
        try {
            return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        } catch (SecurityException | UnsupportedJwtException | IllegalArgumentException e) {
            throw new JwtInvalidException(e);
        } catch (MalformedJwtException e) {
            throw new JwtMalformedException(e);
        } catch (ExpiredJwtException e) {
            // 만료되어도 Claims는 반환 (정보 추출용)
            return e.getClaims();
        }
    }
}
