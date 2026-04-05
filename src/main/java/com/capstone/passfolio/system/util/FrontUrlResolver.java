package com.capstone.passfolio.system.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Slf4j
public class FrontUrlResolver {

    /**
     * 요청의 State 파라미터, Origin 또는 Referer 헤더를 확인하여 허용된 URL 리스트에서 매칭되는 값을 반환합니다.
     * 우선순위: State 파라미터 > Origin 헤더 > Referer 헤더 > 기본 URL
     * 
     * @param request HTTP 요청 객체
     * @param allowedUrls 허용된 URL 리스트 (예: ["http://localhost:5173", "https://domain.com"])
     * @param defaultUrl 매칭되지 않을 경우 사용할 기본 URL
     * @return 매칭된 URL 또는 기본 URL
     */
    public static String resolveUrl(HttpServletRequest request, List<String> allowedUrls, String defaultUrl) {
        log.info("🔍 [FrontUrlResolver] URL 선택 시작");
        log.info("   - 허용된 URL 리스트: {}", allowedUrls);
        log.info("   - 기본 URL: {}", defaultUrl);
        
        if (allowedUrls == null || allowedUrls.isEmpty()) {
            log.warn("⚠️ Allowed URLs list is empty, using default URL: {}", defaultUrl);
            return defaultUrl;
        }

        // 1. State 파라미터 확인 (OAuth2 인증 플로우에서 프론트엔드 URL 전달용)
        String state = request.getParameter("state");
        log.info("   - 요청 State 파라미터: {}", state != null ? state : "(없음)");
        
        if (state != null && !state.isBlank()) {
            // State에서 프론트엔드 URL 추출 시도
            String frontendUrlFromState = extractFrontendUrlFromState(state, allowedUrls);
            if (frontendUrlFromState != null) {
                log.info("✅ [FrontUrlResolver] State 파라미터로 매칭 성공: {} -> {}", state, frontendUrlFromState);
                return frontendUrlFromState;
            } else {
                log.info("   - State 파라미터에서 유효한 프론트엔드 URL 추출 실패");
            }
        }

        // 2. Origin 헤더 확인 (CORS 요청에서 사용)
        String origin = request.getHeader("Origin");
        log.info("   - 요청 Origin 헤더: {}", origin != null ? origin : "(없음)");
        
        if (origin != null && !origin.isBlank()) {
            String matched = findMatchingUrl(origin, allowedUrls);
            if (matched != null) {
                log.info("✅ [FrontUrlResolver] Origin 헤더로 매칭 성공: {} -> {}", origin, matched);
                return matched;
            } else {
                log.info("   - Origin 헤더와 매칭되는 URL 없음");
            }
        }

        // 3. Referer 헤더 확인 (일반 요청에서 사용)
        String referer = request.getHeader("Referer");
        log.info("   - 요청 Referer 헤더: {}", referer != null ? referer : "(없음)");
        
        if (referer != null && !referer.isBlank()) {
            try {
                URI refererUri = new URI(referer);
                String refererOrigin = refererUri.getScheme() + "://" + refererUri.getHost();
                if (refererUri.getPort() != -1) {
                    refererOrigin += ":" + refererUri.getPort();
                }
                log.info("   - Referer에서 추출한 Origin: {}", refererOrigin);
                
                String matched = findMatchingUrl(refererOrigin, allowedUrls);
                if (matched != null) {
                    log.info("✅ [FrontUrlResolver] Referer 헤더로 매칭 성공: {} -> {}", refererOrigin, matched);
                    return matched;
                } else {
                    log.info("   - Referer Origin과 매칭되는 URL 없음");
                }
            } catch (URISyntaxException e) {
                log.warn("⚠️ Invalid Referer URI: {}", referer);
            }
        }

        // 4. 매칭되지 않으면 기본 URL 사용
        log.info("⚠️ [FrontUrlResolver] 매칭되는 URL 없음, 기본 URL 사용: {}", defaultUrl);
        return defaultUrl;
    }

    /**
     * 주어진 origin과 허용된 URL 리스트를 비교하여 매칭되는 URL을 찾습니다.
     * 정확히 일치하거나, 도메인만 일치하는 경우를 처리합니다.
     */
    private static String findMatchingUrl(String origin, List<String> allowedUrls) {
        log.info("   - 매칭 시도: origin={}, allowedUrls={}", origin, allowedUrls);
        
        // 정확히 일치하는 경우
        for (String allowedUrl : allowedUrls) {
            String normalizedOrigin = normalizeUrl(origin);
            String normalizedAllowed = normalizeUrl(allowedUrl);
            log.info("     → 정확 일치 비교: '{}' vs '{}'", normalizedOrigin, normalizedAllowed);
            if (normalizedOrigin.equals(normalizedAllowed)) {
                log.info("     ✅ 정확 일치 발견: {}", allowedUrl);
                return allowedUrl;
            }
        }

        // 도메인만 비교 (포트 제외)
        String originDomain = extractDomain(origin);
        log.info("   - 도메인 추출: origin={} -> domain={}", origin, originDomain);
        
        if (originDomain != null) {
            for (String allowedUrl : allowedUrls) {
                String allowedDomain = extractDomain(allowedUrl);
                log.info("     → 도메인 비교: '{}' vs '{}'", originDomain, allowedDomain);
                if (originDomain.equals(allowedDomain)) {
                    log.info("     ✅ 도메인 일치 발견: {}", allowedUrl);
                    return allowedUrl; // 포트가 포함된 원본 URL 반환
                }
            }
        }

        log.info("   - 매칭 실패: 모든 URL과 일치하지 않음");
        return null;
    }

    /**
     * URL을 정규화합니다 (trailing slash 제거, 소문자 변환 등)
     */
    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String normalized = url.trim().toLowerCase();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * URL에서 도메인 부분만 추출합니다 (scheme + host + port)
     */
    private static String extractDomain(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            
            if (scheme == null || host == null) {
                return null;
            }

            String domain = scheme + "://" + host;
            if (uri.getPort() != -1) {
                domain += ":" + uri.getPort();
            }
            return domain;
        } catch (URISyntaxException e) {
            log.warn("⚠️ Failed to extract domain from URL: {}", url);
            return null;
        }
    }

    /**
     * OAuth2 State 파라미터에서 프론트엔드 URL을 추출합니다.
     * State 형식: "base64_encoded_state|frontend_url" 또는 "frontend_url" 또는 base64 디코딩 후 URL 추출
     * 
     * @param state OAuth2 state 파라미터 값
     * @param allowedUrls 허용된 URL 리스트
     * @return 매칭되는 프론트엔드 URL 또는 null
     */
    private static String extractFrontendUrlFromState(String state, List<String> allowedUrls) {
        log.info("🟢 extractFrontendUrlFromState Start");
        if (state == null || state.isBlank() || allowedUrls == null || allowedUrls.isEmpty()) {
            return null;
        }

        try {
            // State에서 URL 추출 시도 (여러 형식 지원)
            String candidateUrl = null;

            // 1. State가 직접 URL인 경우 (예: "http://localhost:3000")
            if (state.startsWith("http://") || state.startsWith("https://")) {
                candidateUrl = state;
            }
            // 2. State가 "separator|url" 형식인 경우 (예: "random_state|http://localhost:3000")
            else if (state.contains("|")) {
                String[] parts = state.split("\\|", 2);
                if (parts.length == 2 && (parts[1].startsWith("http://") || parts[1].startsWith("https://"))) {
                    candidateUrl = parts[1];
                }
            }
            // 3. Base64 디코딩 시도
            else {
                try {
                    byte[] decoded = java.util.Base64.getUrlDecoder().decode(state);
                    String decodedState = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                    
                    // 디코딩된 값이 URL인 경우
                    if (decodedState.startsWith("http://") || decodedState.startsWith("https://")) {
                        candidateUrl = decodedState;
                    }
                    // 디코딩된 값이 "separator|url" 형식인 경우
                    else if (decodedState.contains("|")) {
                        String[] parts = decodedState.split("\\|", 2);
                        if (parts.length == 2 && (parts[1].startsWith("http://") || parts[1].startsWith("https://"))) {
                            candidateUrl = parts[1];
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // Base64 디코딩 실패는 무시 (State가 다른 형식일 수 있음)
                    log.debug("State 파라미터 Base64 디코딩 실패 (정상일 수 있음): {}", state);
                }
            }

            // 추출한 URL이 허용된 URL 목록과 매칭되는지 확인
            if (candidateUrl != null) {
                String matched = findMatchingUrl(candidateUrl, allowedUrls);
                if (matched != null) {
                    log.info("   - State에서 프론트엔드 URL 추출 성공: {} -> {}", candidateUrl, matched);
                    return matched;
                } else {
                    log.warn("⚠️ State에서 추출한 URL이 허용 목록에 없음: {}", candidateUrl);
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ State 파라미터에서 URL 추출 중 오류 발생: {}", e.getMessage());
        }

        return null;
    }
}
