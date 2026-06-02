package com.capstone.passfolio.system.security.config;

import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 내부 호출 인증 필터(X-INTERNAL-API-KEY). 경로별로 기대 키·실패 에러코드를 매핑한다.
 * (단일 키 → 경로별 키로 확장. 기존 AI 경로는 동일 키·동일 에러코드로 동작 불변.)
 */
@Slf4j
public class InternalApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-INTERNAL-API-KEY";

    /** 보호 경로 단위: 정확히 일치하는 URI에 대해 expectedKey 검증, 실패 시 errorCode 응답. */
    public record ProtectedPath(String path, String expectedKey, ErrorCode errorCode) { }

    private final List<ProtectedPath> protectedPaths;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(List<ProtectedPath> protectedPaths, ObjectMapper objectMapper) {
        this.protectedPaths = protectedPaths;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return match(request) == null;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        ProtectedPath protectedPath = match(request);
        if (protectedPath == null) { // shouldNotFilter가 이미 걸러내지만 방어적으로
            filterChain.doFilter(request, response);
            return;
        }
        String providedKey = request.getHeader(HEADER_NAME);
        if (providedKey == null || !protectedPath.expectedKey().equals(providedKey)) {
            log.warn("Invalid or missing internal API key. URI={}, RemoteAddr={}",
                    request.getRequestURI(), request.getRemoteAddr());
            writeErrorResponse(response, protectedPath.errorCode());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private ProtectedPath match(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return protectedPaths.stream()
                .filter(p -> p.path().equals(uri))
                .findFirst()
                .orElse(null);
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        ErrorResponse errorResponse = ErrorResponse.of(errorCode);
        response.setStatus(errorResponse.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
