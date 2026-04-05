package com.capstone.passfolio.domain.auth.oauth2.handler;

import com.capstone.passfolio.system.util.FrontUrlResolver;
import com.capstone.passfolio.system.util.PropertiesParserUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.front-base-url}")
    private String frontBaseUrlConfig;

    @Override
    public void onAuthenticationFailure(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        log.warn("OAuth2 로그인 실패: {}", exception.getMessage());

        String code = "auth_failed";

        if (exception instanceof OAuth2AuthenticationException oae
                && oae.getError() != null
                && oae.getError().getErrorCode() != null) {
            code = oae.getError().getErrorCode();
        }

        // 요청의 Origin/Referer에 따라 적절한 프론트엔드 URL 선택
        log.info("🔍 [CustomFailureHandler] front-base-url 선택 시작");
        log.info("   - 설정값 (front-base-url): {}", frontBaseUrlConfig);
        
        List<String> allowedBaseUrls = PropertiesParserUtils.propertiesParser(frontBaseUrlConfig);
        log.info("   - 파싱된 허용 base URL 리스트: {}", allowedBaseUrls);
        
        String frontBaseUrl = FrontUrlResolver.resolveUrl(request, allowedBaseUrls, allowedBaseUrls.get(0));
        
        String target = frontBaseUrl + "/login?error="
                + URLEncoder.encode(code, StandardCharsets.UTF_8);

        log.info("🟡 [CustomFailureHandler] 최종 선택된 에러 페이지 URL: {}", target);
        log.info("   - 선택된 front-base-url: {}", frontBaseUrl);
        log.info("   - 요청 Origin: {}", request.getHeader("Origin"));
        log.info("   - 요청 Referer: {}", request.getHeader("Referer"));
        log.info("   - 요청 State 파라미터: {}", request.getParameter("state"));

        response.sendRedirect(target);
    }
}
