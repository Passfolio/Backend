package com.capstone.passfolio.system.security.jwt.config;

import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.security.config.RequestMatcherHolder;
import com.capstone.passfolio.system.security.jwt.dto.JwtDto;
import com.capstone.passfolio.system.security.jwt.exception.*;
import com.capstone.passfolio.system.security.jwt.util.JwtTokenResolver;
import com.capstone.passfolio.system.security.jwt.util.JwtTokenValidator;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import com.capstone.passfolio.system.util.UserLoadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenResolver jwtTokenResolver;
    private final UserLoadService userLoadService;
    private final JwtTokenValidator jwtTokenValidator;
    private final RequestMatcherHolder requestMatcherHolder;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // 1. RequestMatcherHolder의 permitAll 경로는 필터 스킵
        if (requestMatcherHolder.getRequestMatchersByMinRole(null).matches(request)) {
            return true;
        }

        // 2. /api/v1/**가 아닌 경로는 필터 스킵 (SecurityConfig에서 denyAll()로 차단됨)
        String uri = request.getRequestURI();
        if (uri != null && !uri.startsWith("/api/v1/")) {
            return true; // 필터 스킵 (SecurityConfig에서 처리)
        }

        // 3. /api/v1/** 경로는 필터 통과 (인증 필요)
        return false;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 디버깅: 쿠키가 없을 때만 상세 로그 출력
        // /api/** 경로만 필터를 통과하므로 여기서는 정상적인 API 요청만 처리
        if (request.getCookies() == null || request.getCookies().length == 0) {
            String uri = request.getRequestURI();
            // /api/** 경로는 정상적인 API 요청이므로 WARN 레벨 유지
            log.warn("⚠️ No cookies in request - URI: {}, Method: {}, Origin: {}, Referer: {}, Cookie Header: {}, All Headers: {}", 
                    uri, 
                    request.getMethod(),
                    request.getHeader("Origin"),
                    request.getHeader("Referer"),
                    request.getHeader("Cookie"),
                    Collections.list(request.getHeaderNames()).stream()
                            .map(name -> name + "=" + request.getHeader(name))
                            .collect(Collectors.joining(", ")));
        }

        try {
            // Parse Token From Request
            var nullableToken = jwtTokenResolver.parseTokenFromRequest(request);
            // if (nullableToken.isEmpty()) { throw new JwtMissingException(); }
            if (nullableToken.isEmpty()) { filterChain.doFilter(request, response); return; }

            // Extract JWT Payload with Validation (Token 자체의 유효성 검증)
            String tokenString = nullableToken.get();
            log.debug("🔍 Attempting to resolve token - Token length: {}", tokenString.length());
            JwtDto.TokenPayload payload;
            try {
                payload = jwtTokenResolver.resolveToken(tokenString);
                log.debug("✅ Token resolved successfully - Subject: {}, Type: {}", payload.getSubject(), payload.getTokenType());
            } catch (Exception e) {
                log.error("❌ Token resolution failed - Error: {}, Message: {}", e.getClass().getSimpleName(), e.getMessage(), e);
                throw e;
            }

            // ATK Validation: isAtk? isValidJti? isBlacklist? (사용 목적에 따른 유효성 검증)
            try {
                jwtTokenValidator.validateAtk(payload);
                log.debug("✅ ATK validation passed - JTI: {}", payload.getJti());
            } catch (Exception e) {
                log.error("❌ ATK validation failed - Error: {}, Message: {}", e.getClass().getSimpleName(), e.getMessage());
                throw e;
            }

            // Define UserPrincipal
            UserPrincipal userPrincipal;
            try {
                userPrincipal = userLoadService.loadUserById(Long.valueOf(payload.getSubject()))
                        .orElseThrow(() -> {
                            log.error("❌ User not found - Subject: {}", payload.getSubject());
                            return new JwtInvalidException();
                        });
                log.debug("✅ UserPrincipal loaded - UserId: {}, Username: {}", userPrincipal.getUserId(), userPrincipal.getUsername());
            } catch (NumberFormatException e) {
                log.error("❌ Invalid subject format - Subject: {}", payload.getSubject());
                throw new JwtInvalidException(e);
            }

            // Create Authentication Instance
            Authentication authentication = createAuthentication(userPrincipal);

            // Register Authentication to SecurityContextHolder
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("🟢 JWT authentication successful for user: {}", userPrincipal.getUsername());
        } catch (JwtInvalidException e) {
            log.error("⚠️ JWT authentication failed", e);
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.JWT_INVALID);
            return;
        } catch (JwtMissingException e) {
            // /api/** 경로만 필터를 통과하므로 여기서는 정상적인 API 요청만 처리
            String uri = request.getRequestURI();
            log.warn("⚠️ No JWT token found in request - URI: {}, Method: {}", uri, request.getMethod());
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.JWT_MISSING);
            return;
        } catch (JwtExpiredException e) {
            log.warn("⚠️ JWT token has expired", e);
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.JWT_EXPIRED);
            return;
        } catch (JwtMalformedException e) {
            log.error("⚠️ JWT token is malformed", e);
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.JWT_MALFORMED);
            return;
        } catch (JwtBlacklistException e) {
            log.error("⚠️ JWT token is blacklisted", e);
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.JWT_BLACKLIST);
            return;
        } catch (Exception e) {
            log.error("⚠️ Unexpected error during JWT authentication", e);
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Authentication createAuthentication(UserPrincipal userPrincipal) {
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority(userPrincipal.getRole().name()));

        return new UsernamePasswordAuthenticationToken(userPrincipal, null, authorities);
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        ErrorResponse errorResponse = ErrorResponse.of(errorCode);
        response.setStatus(errorResponse.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

}

