package com.capstone.passfolio.domain.auth.service;

import com.capstone.passfolio.domain.user.entity.User;
import com.capstone.passfolio.domain.user.repository.UserRepository;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import com.capstone.passfolio.system.security.jwt.dto.JwtDto;
import com.capstone.passfolio.system.security.jwt.service.TokenService;
import com.capstone.passfolio.system.security.jwt.util.JwtTokenResolver;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import com.capstone.passfolio.system.util.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtTokenResolver jwtTokenResolver;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final CookieUtils cookieUtils;

    private final ClientRegistrationRepository clientRegistrationRepository;

    @Value("${app.front-base-url}")
    private String frontBaseUrlConfig;

    @Value("${app.backend-base-url:}")
    private String backendBaseUrlConfig;

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = jwtTokenResolver.parseTokenFromRequest(request)
                .orElseThrow(() -> new RestException(ErrorCode.JWT_MISSING));

        String refreshToken = jwtTokenResolver.parseRefreshTokenFromRequest(request)
                .orElseThrow(() -> new RestException(ErrorCode.JWT_MISSING));

        // 1) 서버 측 토큰(ATK/RTK) 무효화 (예: Redis blacklist)
        tokenService.clearTokensByAtkWithValidation(accessToken, refreshToken);

        // 2) 브라우저 쿠키 제거 (same name, same path, domain 등으로)
        clearCookies(response);
    }

    @Transactional
    public void delete(UserPrincipal userPrincipal, HttpServletRequest request, HttpServletResponse response) {
        User foundUser = userRepository.findById(userPrincipal.getUserId())
                .orElseThrow(() -> new RestException(ErrorCode.USER_NOT_FOUND));

        String accessToken = jwtTokenResolver.parseTokenFromRequest(request)
                .orElseThrow(() -> new RestException(ErrorCode.JWT_MISSING));

        String refreshToken = jwtTokenResolver.parseRefreshTokenFromRequest(request)
                .orElseThrow(() -> new RestException(ErrorCode.JWT_MISSING));

        tokenService.clearTokensByAtkWithValidation(accessToken, refreshToken);

        // Hard Delete Bulk 연산으로 명시적으로 한번에 지운다.
        userRepository.deleteByUserId(foundUser.getId()); // TODO: Bulk 연산 userRepository에 반영할 것

        clearCookies(response);
    }

    @Transactional
    public JwtDto.TokenExpiresInfo refreshTokens(
            HttpServletRequest request,
            HttpServletResponse response,
            boolean rememberMe) {

        // 소셜 로그인을 위한 처리 -> 자동 로그인이면 QueryParam으로 입력받는다.
        JwtDto.TokenOptionWrapper tokenOption =
                JwtDto.TokenOptionWrapper.of(request, response, rememberMe);
        JwtDto.TokenInfo tokenInfo = tokenService.rotateByRtkWithValidation(tokenOption);
        return JwtDto.TokenExpiresInfo.from(tokenInfo);
    }

    public boolean isRtkBlacklisted(String refreshToken) {
        return tokenService.isRtkBlacklisted(refreshToken);
    }

    public boolean isAtkBlacklisted(String accessToken) {
        return tokenService.isAtkBlacklisted(accessToken);
    }

    public boolean isTokenActive(HttpServletRequest request) {
        return tokenService.validateTokens(request);
    }

    // Helper Methods
    private void clearCookies(HttpServletResponse response) {
        cookieUtils.clearAccessTokenCookie(response);
        cookieUtils.clearRefreshTokenCookie(response);
    }

    private void setCookies(HttpServletResponse response, JwtDto.TokenInfo tokenInfo) {
        cookieUtils.addAccessTokenCookie(response, tokenInfo.getAccessToken(), tokenInfo.getRefreshTokenExpiresAt());
        cookieUtils.addRefreshTokenCookie(response, tokenInfo.getRefreshToken(), tokenInfo.getRefreshTokenExpiresAt());
    }
}
