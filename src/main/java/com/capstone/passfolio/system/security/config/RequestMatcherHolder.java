package com.capstone.passfolio.system.security.config;

import com.capstone.passfolio.domain.user.entity.enums.Role;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestMatcherHolder {

    @Value("${management.endpoints.web.base-path}") private String actuatorBasePath;
    @Value("${springdoc.swagger-ui.path}") private String swaggerUiPath;
    @Value("${springdoc.api-docs.path}") private String swaggerSpecPath;

    // PathPattern 파서를 한 번만 준비 (thread-safe)
    private static final PathPatternParser PARSER = new PathPatternParser();

    private static final List<RequestInfo> REQUEST_INFO_LIST = List.of(
            // auth
            new RequestInfo(HttpMethod.POST, "/api/v1/auth/register", null),
            new RequestInfo(HttpMethod.POST, "/api/v1/auth/login", null),
            new RequestInfo(HttpMethod.POST, "/api/v1/auth/refresh", null),
            new RequestInfo(HttpMethod.POST, "/api/v1/auth/is-blacklisted-rtk", null),
            new RequestInfo(HttpMethod.POST, "/api/v1/auth/is-blacklisted-atk", null),
            new RequestInfo(HttpMethod.GET, "/api/v1/auth/logout/callback", null),

            // system
            new RequestInfo(null, "/api/v1/system/**", null),

            // Swagger UI·OpenAPI가 /api/v1 아래에 있을 때: 만료 ATK/RTK 쿠키가 있어도 문서 접근 가능해야 함
            new RequestInfo(null, "/api/v1/swagger/**", null),

            new RequestInfo(HttpMethod.GET, "/oembed", null),      // oEmbed JSON Provider
            new RequestInfo(HttpMethod.GET, "/share/**", null),    // Thymeleaf Share Page

            // Search
            new RequestInfo(HttpMethod.GET, "/api/v1/spec/search/**", null),

            // static resources
            new RequestInfo(HttpMethod.GET,  "/docs/**", null),
            new RequestInfo(HttpMethod.GET,  "/*.ico", null),
            new RequestInfo(HttpMethod.GET,  "/resources/**", null),
            new RequestInfo(HttpMethod.GET,  "/error", null),
            new RequestInfo(HttpMethod.GET,  "/", null),

            // robots (JWT 미적용 / permitAll)
            new RequestInfo(HttpMethod.GET, "/robots.txt", null)

    );

    // SecurityConfig에 직접 명시된 permitAll 엔드포인트들 (비회원/회원 모두 접근 가능)
    private static final List<RequestInfo> SECURITY_CONFIG_PERMIT_ALL_LIST = List.of(
    );

    private final ConcurrentHashMap<String, RequestMatcher> reqMatcherCacheMap = new ConcurrentHashMap<>();

    /**
     * 최소 권한이 주어진 요청에 대한 RequestMatcher 반환
     */
    public RequestMatcher getRequestMatchersByMinRole(@Nullable Role minRole) {
        var key = (minRole == null ? "VISITOR" : minRole.name());
        return reqMatcherCacheMap.computeIfAbsent(key, k -> {
            var matchers = REQUEST_INFO_LIST.stream()
                    .filter(req -> Objects.equals(req.minRole(), minRole))
                    .map(this::toRequestMatcher)     // ← PathPattern 기반 매처로 변환
                    .toArray(RequestMatcher[]::new);

            // actuator: 런타임에 주입된 basePath로 생성
            String base = actuatorBasePath;
            if (base == null || base.isBlank()) throw new IllegalStateException("actuatorBasePath is blank");
            if (!base.startsWith("/")) base = "/" + base;
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String actuatorPattern = base + "/**";

            RequestMatcher actuatorMatcher = toRequestMatcher(
                    new RequestInfo(HttpMethod.GET, actuatorPattern, null)
            );

            RequestMatcher swaggerUiMatcher = swaggerTreeMatcher(swaggerUiPath);
            RequestMatcher swaggerApiMatcher = swaggerTreeMatcher(swaggerSpecPath);

            // 기존 + 동적 Path 합치기
            RequestMatcher[] merged = new RequestMatcher[matchers.length + 3];
            System.arraycopy(matchers, 0, merged, 0, matchers.length);

            // actuator
            merged[matchers.length] = actuatorMatcher;

            // swagger
            merged[matchers.length + 1] = swaggerUiMatcher;
            merged[matchers.length + 2] = swaggerApiMatcher;

            return new OrRequestMatcher(merged);
        });
    }

    /**
     * SecurityConfig에 직접 명시된 permitAll 엔드포인트들에 대한 RequestMatcher 반환
     * @return SecurityConfig의 permitAll 엔드포인트면 true
     */
    public RequestMatcher getSecurityConfigPermitAllMatcher() {
        return reqMatcherCacheMap.computeIfAbsent("SECURITY_CONFIG_PERMIT_ALL", k -> {
            var matchers = SECURITY_CONFIG_PERMIT_ALL_LIST.stream()
                    .map(this::toRequestMatcher)
                    .toArray(RequestMatcher[]::new);
            return new OrRequestMatcher(matchers);
        });
    }

    /**
     * /api/v1/**로 시작하는 모든 경로에 대한 RequestMatcher 반환
     * @return /api/v1/**로 시작하는 경로면 true
     */
    public RequestMatcher getApiRequestMatcher() {
        return reqMatcherCacheMap.computeIfAbsent("API_PREFIX", k -> {
            final PathPattern apiPattern = PARSER.parse("/api/v1/**");
            return (HttpServletRequest request) -> {
                String uri = request.getRequestURI();
                String contextPath = request.getContextPath();
                if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
                    uri = uri.substring(contextPath.length());
                }
                return apiPattern.matches(PathContainer.parsePath(uri));
            };
        });
    }

    /**
     * 단일 항목을 PathPattern 기반 RequestMatcher 로 변환
     */
    private static String normalizeSwaggerPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * springdoc 경로가 비어 있으면 {@code /**} 같은 위험한 패턴을 만들지 않고 매칭하지 않는다.
     */
    private RequestMatcher swaggerTreeMatcher(String configuredPath) {
        String normalized = normalizeSwaggerPath(configuredPath);
        if (normalized.isBlank()) {
            return request -> false;
        }
        return toRequestMatcher(new RequestInfo(null, normalized + "/**", null));
    }

    private RequestMatcher toRequestMatcher(RequestInfo info) {
        final PathPattern pattern = PARSER.parse(info.pattern());
        final HttpMethod method = info.method();

        return (HttpServletRequest request) -> {
            // 1) HTTP Method 체크
            if (method != null && !method.name().equalsIgnoreCase(request.getMethod())) { return false; }

            // 2) context-path 제거 후 PathPattern 매칭
            String uri = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
                uri = uri.substring(contextPath.length());
            }
            return pattern.matches(PathContainer.parsePath(uri));
        };
    }

    private record RequestInfo(HttpMethod method, String pattern, Role minRole) { }
}
