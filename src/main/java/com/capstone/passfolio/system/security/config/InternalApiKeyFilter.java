package com.capstone.passfolio.system.security.config;

import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class InternalApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-INTERNAL-API-KEY";

    private final String internalApiKey;
    private final ObjectMapper objectMapper;

    private static final List<String> PROTECTED_PATHS = List.of(
            "/api/v1/ai/jobs/complete"
    );

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return PROTECTED_PATHS.stream().noneMatch(request.getRequestURI()::equals);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String providedKey = request.getHeader(HEADER_NAME);
        if (providedKey == null || !internalApiKey.equals(providedKey)) {
            log.warn("Invalid or missing internal API key. URI={}, RemoteAddr={}",
                    request.getRequestURI(), request.getRemoteAddr());
            writeErrorResponse(response, ErrorCode.AI_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        ErrorResponse errorResponse = ErrorResponse.of(errorCode);
        response.setStatus(errorResponse.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
