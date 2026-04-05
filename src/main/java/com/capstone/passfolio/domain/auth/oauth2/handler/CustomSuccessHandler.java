package com.capstone.passfolio.domain.auth.oauth2.handler;

import com.capstone.passfolio.domain.auth.oauth2.entity.CustomOAuth2User;
import com.capstone.passfolio.system.security.jwt.dto.JwtDto;
import com.capstone.passfolio.system.security.jwt.service.TokenService;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import com.capstone.passfolio.system.util.CookieUtils;
import com.capstone.passfolio.system.util.FrontUrlResolver;
import com.capstone.passfolio.system.util.PropertiesParserUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final TokenService tokenService;
    private final CookieUtils cookieUtils;

    @Value("${app.front-redirect-uri}") private String frontRedirectUriConfig;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CustomOAuth2User oauth2)) {
            log.info("✅ CustomSuccessHandler principal not instance of CustomOAuth2User: {}", principal.getClass());
            getRedirectStrategy().sendRedirect(request, response, "/"); // fallback: 기본 유저 처리 or 에러
            return;
        }

        // 1) PK/Role 기반 UserPrincipal 구성 (패스워드는 null)
        UserPrincipal userPrincipal = UserPrincipal.toOAuth2(oauth2);

        // 2) TokenService로 토큰 페어 발급(+Redis 화이트리스트 등록)
        JwtDto.TokenOptionWrapper tokenOption = JwtDto.TokenOptionWrapper.of(userPrincipal, false);
        JwtDto.TokenInfo tokenInfo = tokenService.issueTokens(tokenOption);

        log.info("🟢 Issued Tokens - ATK: {}, RTK: {}", tokenInfo.getAccessToken(), tokenInfo.getRefreshToken());

        // 4) 보안 쿠키 설정
        cookieUtils.addAccessTokenCookie(response, tokenInfo.getAccessToken(), tokenInfo.getRefreshTokenExpiresAt());
        cookieUtils.addRefreshTokenCookie(response, tokenInfo.getRefreshToken(), tokenInfo.getRefreshTokenExpiresAt());

        // 5) 요청의 Origin/Referer에 따라 적절한 리다이렉트 URI 선택
        log.info("🔍 [CustomSuccessHandler] front-redirect-uri 선택 시작");
        log.info("   - 설정값 (front-redirect-uri): {}", frontRedirectUriConfig);
        
        List<String> allowedRedirectUris = PropertiesParserUtils.propertiesParser(frontRedirectUriConfig);
        log.info("   - 파싱된 허용 리다이렉트 URI 리스트: {}", allowedRedirectUris);
        
        String redirectUri = FrontUrlResolver.resolveUrl(request, allowedRedirectUris, allowedRedirectUris.get(0));
        
        log.info("🟢 [CustomSuccessHandler] 최종 선택된 리다이렉트 URI: {}", redirectUri);
        log.info("   - 요청 Origin: {}", request.getHeader("Origin"));
        log.info("   - 요청 Referer: {}", request.getHeader("Referer"));
        log.info("   - 요청 State 파라미터: {}", request.getParameter("state"));

        // 6) FE로 리다이렉트 (토큰은 쿠키로 전달되므로 URL 노출 없음)
        getRedirectStrategy().sendRedirect(request, response, redirectUri);
    }
}
