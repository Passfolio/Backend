package com.capstone.passfolio.system.security.jwt.config;

import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.security.config.RequestMatcherHolder;
import com.capstone.passfolio.system.security.jwt.dto.JwtDto;
import com.capstone.passfolio.system.security.jwt.dto.TokenType;
import com.capstone.passfolio.system.security.jwt.exception.*;
import com.capstone.passfolio.system.security.jwt.service.TokenService;
import com.capstone.passfolio.system.security.jwt.util.JwtTokenResolver;
import com.capstone.passfolio.system.security.jwt.util.JwtTokenValidator;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import com.capstone.passfolio.system.util.CookieUtils;
import com.capstone.passfolio.system.util.UserLoadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.UnsupportedJwtException;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenResolver jwtTokenResolver;
    private final UserLoadService userLoadService;
    private final JwtTokenValidator jwtTokenValidator;
    private final RequestMatcherHolder requestMatcherHolder;
    private final ObjectMapper objectMapper;
    private final TokenService tokenService;
    private final CookieUtils cookieUtils;

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
            log.warn("⚠️ JWT token has expired, checking refresh token for auto-login", e.getMessage());

            // SecurityConfig에 직접 명시된 permitAll 엔드포인트인지 확인
            boolean isPermitAll = requestMatcherHolder.getSecurityConfigPermitAllMatcher().matches(request);

            // TODO: Refactoring 필요 -> 별도의 Helper Methods 로 분리할 것
            // ATK 만료 시 RTK 확인 및 검증 (자동 로그인 지원)
            Optional<String> nullableRtk = Optional.empty();
            try {
                // 1. RTK 존재 여부 확인 (ATK는 없어도 RTK만 있으면 자동 Refresh 가능)
                nullableRtk = jwtTokenResolver.parseRefreshTokenFromRequest(request);
                if (nullableRtk.isEmpty()) {
                    log.warn("⚪ No refresh token found, cannot auto-refresh");
                    SecurityContextHolder.clearContext();

                    clearCookies(response);

                    // permitAll 엔드포인트면 에러 반환하지 않고 필터 통과 (비회원으로 처리)
                    if (isPermitAll) {
                        log.debug("🟢 PermitAll endpoint - allowing request without authentication");
                        filterChain.doFilter(request, response);
                        return;
                    }

                    writeErrorResponse(response, ErrorCode.JWT_MISSING);
                    return;
                }
                
                // 2. RTK 파싱 및 기본 검증 (UUID/블랙리스트 검증은 TokenService의 Lock 내부에서 수행)
                JwtDto.TokenPayload rtkPayload = jwtTokenResolver.resolveToken(nullableRtk.get());

                // 기본 검증만 수행 (Race Condition 대응을 위해 UUID 검증은 제외)
                if (rtkPayload.getTokenType() != TokenType.REFRESH) {
                    log.warn("⚠️ RTK validation failed: TokenType이 REFRESH가 아님 - {}", rtkPayload.getTokenType());
                    throw new JwtInvalidException();
                }
                if (rtkPayload.getSubject() == null || rtkPayload.getSubject().isEmpty()) {
                    log.warn("⚠️ RTK validation failed: Subject가 null이거나 비어있음");
                    throw new JwtInvalidException();
                }
                if (rtkPayload.getRefreshUuid() == null || rtkPayload.getRefreshUuid().isEmpty()) {
                    log.warn("⚠️ RTK validation failed: RefreshUuid가 null이거나 비어있음");
                    throw new JwtInvalidException();
                }

                // 3. RTK가 유효하면 자동 Refresh 처리 (UUID 검증은 TokenService의 Lock 내부에서 수행)
                log.info("🟢 Valid refresh token found, performing auto-refresh");
                try {
                    boolean rememberMe = rtkPayload.getRememberMe() != null && rtkPayload.getRememberMe();
                    
                    // TokenService를 통해 자동 Refresh (Filter에서 검증된 rtkPayload 전달 → 이중 파싱 방지)
                    JwtDto.TokenOptionWrapper tokenOption = JwtDto.TokenOptionWrapper.builder()
                            .httpServletRequest(request)
                            .httpServletResponse(response)
                            .rememberMe(rememberMe)
                            .rtkPayload(rtkPayload)
                            .build();
                    JwtDto.TokenInfo tokenInfo = tokenService.rotateByRtkWithValidation(tokenOption);
                    
                    // 새로 발급된 RTK를 request attribute에 저장 (같은 요청에서 사용하기 위해)
                    request.setAttribute("NEW_REFRESH_TOKEN", tokenInfo.getRefreshToken());
                    
                    // 새로 발급된 ATK를 직접 사용 (쿠키에서 읽지 않음 - 같은 요청에서는 쿠키가 반영되지 않음)
                    String newAccessToken = tokenInfo.getAccessToken();
                    JwtDto.TokenPayload newPayload = jwtTokenResolver.resolveToken(newAccessToken);
                    jwtTokenValidator.validateAtk(newPayload);
                    
                    UserPrincipal userPrincipal = userLoadService.loadUserById(Long.valueOf(newPayload.getSubject()))
                            .orElseThrow(JwtInvalidException::new);
                    
                    Authentication authentication = createAuthentication(userPrincipal);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    log.info("🟢 Auto-refresh successful, new authentication set for user: {}", userPrincipal.getUsername());
                    filterChain.doFilter(request, response);
                    return;
                } catch (Exception refreshException) {
                    log.error("⚠️ Auto-refresh failed: {}", refreshException.getMessage(), refreshException);
                    SecurityContextHolder.clearContext();

                    // RTK 만료 = 세션 종료 → 쿠키 삭제 + JWT_SESSION_EXPIRED
                    if (refreshException instanceof JwtExpiredException) {
                        clearCookies(response);
                        log.warn("⚠️ Refresh token expired - session terminated, redirecting to login");

                        if (isPermitAll) {
                            log.debug("🟢 PermitAll endpoint - allowing request without authentication after RTK expiration");
                            filterChain.doFilter(request, response);
                            return;
                        }

                        writeErrorResponse(response, ErrorCode.JWT_SESSION_EXPIRED);
                        return;
                    }

                    // Transient failure (race condition) → 쿠키 유지 (FE 재시도 가능)
                    // Non-transient failure (세션 종료, 토큰 무효 등) → 쿠키 삭제
                    boolean isTransient = isTransientRtkFailure(refreshException);
                    if (!isTransient) {
                        clearCookies(response);
                    }

                    if (isPermitAll) {
                        log.debug("🟢 PermitAll endpoint - allowing request without authentication after refresh failure");
                        filterChain.doFilter(request, response);
                        return;
                    }

                    // transient → JWT_EXPIRED (FE 재시도 가능), non-transient → JWT_INVALID (FE 로그인 유도)
                    writeErrorResponse(response, isTransient ? ErrorCode.JWT_EXPIRED : ErrorCode.JWT_INVALID);
                    return;
                }
                
            } catch (Exception rtkException) {
                // RTK 검증 실패 또는 기타 예외 - 구체적인 원인 파악을 위한 상세 로깅
                String rtkToken = nullableRtk.isPresent() ? nullableRtk.get() : null;
                logDetailedRtkValidationFailure(rtkException, rtkToken, request);
                SecurityContextHolder.clearContext();

                // RTK 자체가 만료된 경우 → 세션 종료 (JWT_SESSION_EXPIRED)
                if (rtkException instanceof JwtExpiredException) {
                    clearCookies(response);
                    log.warn("⚠️ Refresh token expired - session terminated, redirecting to login");

                    if (isPermitAll) {
                        log.debug("🟢 PermitAll endpoint - allowing request without authentication after RTK expiration");
                        filterChain.doFilter(request, response);
                        return;
                    }

                    writeErrorResponse(response, ErrorCode.JWT_SESSION_EXPIRED);
                    return;
                }

                // RTK가 존재하지만 검증 실패한 경우 원인별 쿠키 처리:
                // - JwtInvalidException 중 UUID 불일치만: 동시 요청으로 인한 race condition 가능성 → 쿠키 유지
                // - JwtBlacklistException: rotate로 인한 블랙리스트 → 쿠키 유지 (FE 재시도 시 새 쿠키로 성공)
                // - 그 외 JwtInvalidException: 진짜 문제 (TokenType 불일치, Subject/RefreshUuid null, Redis 미존재 등) → 쿠키 삭제
                boolean isTransientFailure = isTransientRtkFailure(rtkException);
                if (!isTransientFailure) {
                    clearCookies(response);
                }

                // permitAll 엔드포인트면 에러 반환하지 않고 필터 통과 (비회원으로 처리)
                if (isPermitAll) {
                    log.debug("🟢 PermitAll endpoint - allowing request without authentication after RTK validation failure");
                    filterChain.doFilter(request, response);
                    return;
                }

                // transient → JWT_EXPIRED (FE 재시도 가능), non-transient → JWT_INVALID (FE 로그인 유도)
                writeErrorResponse(response, isTransientFailure ? ErrorCode.JWT_EXPIRED : ErrorCode.JWT_INVALID);
                return;
            }
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

    private void clearCookies(HttpServletResponse response) {
        cookieUtils.clearAccessTokenCookie(response);
        cookieUtils.clearRefreshTokenCookie(response);
    }

    /**
     * RTK 검증 실패 시 구체적인 원인을 파악하기 위한 상세 로깅
     * @param rtkException 발생한 예외
     * @param rtkToken RTK 토큰 문자열 (null 가능)
     * @param request HTTP 요청 객체
     */
    private void logDetailedRtkValidationFailure(Exception rtkException, String rtkToken, HttpServletRequest request) {
        Throwable cause = rtkException.getCause();
        String causeInfo = cause != null ? 
            String.format(" (원인: %s - %s)", cause.getClass().getSimpleName(), cause.getMessage()) : "";

        // RTK 토큰 정보 (일부만 마스킹)
        String rtkTokenInfo = rtkToken != null ? 
            String.format(" (토큰: %s...%s, 길이: %d)", 
                rtkToken.substring(0, Math.min(20, rtkToken.length())),
                rtkToken.length() > 20 ? rtkToken.substring(rtkToken.length() - 10) : "",
                rtkToken.length()) : " (토큰: null)";

        // 요청 정보
        String requestInfo = String.format(" [URI: %s, Method: %s]", 
            request.getRequestURI(), request.getMethod());

        if (rtkException instanceof JwtInvalidException) {
            if (cause instanceof SecurityException) {
                log.warn("⚠️ Refresh token validation failed: 시크릿 키 불일치{}{}{}", 
                    causeInfo, rtkTokenInfo, requestInfo, rtkException);
            } else if (cause instanceof UnsupportedJwtException) {
                log.warn("⚠️ Refresh token validation failed: 지원하지 않는 JWT 형식{}{}{}", 
                    causeInfo, rtkTokenInfo, requestInfo, rtkException);
            } else if (cause instanceof IllegalArgumentException) {
                log.warn("⚠️ Refresh token validation failed: 잘못된 인자{}{}{}", 
                    causeInfo, rtkTokenInfo, requestInfo, rtkException);
            } else if (cause != null) {
                log.warn("⚠️ Refresh token validation failed: 유효하지 않은 토큰 [원인: {}]{}{}{}", 
                    cause.getClass().getSimpleName(), causeInfo, rtkTokenInfo, requestInfo, rtkException);
            } else {
                // cause가 null인 경우 - validateRtk에서 던진 JwtInvalidException
                // 로그 메시지에서 원인을 추론할 수 있도록 상세 정보 포함
                String rtkPayloadInfo = "";
                try {
                    if (rtkToken != null) {
                        // RTK 파싱 시도 (만료되지 않았다면)
                        try {
                            JwtDto.TokenPayload payload = jwtTokenResolver.resolveToken(rtkToken);
                            rtkPayloadInfo = String.format(" [Payload: Subject=%s, TokenType=%s, RefreshUuid=%s]", 
                                payload.getSubject(), 
                                payload.getTokenType(), 
                                payload.getRefreshUuid());
                        } catch (JwtExpiredException e) {
                            rtkPayloadInfo = " [RTK 파싱 실패: 만료됨]";
                        } catch (Exception e) {
                            rtkPayloadInfo = String.format(" [RTK 파싱 실패: %s]", e.getClass().getSimpleName());
                        }
                    }
                } catch (Exception e) {
                    // 파싱 실패는 무시 (이미 예외가 발생한 상태)
                }

                String exceptionMsg = String.format(" [예외 메시지: %s]", rtkException.getMessage());
                log.error("🔥 Critical: Refresh token validation failed - 원인 불명 (cause=null){}{}{}{}", 
                    rtkTokenInfo, rtkPayloadInfo, requestInfo, exceptionMsg, rtkException);
                
                // 추가 디버깅 정보
                log.error("🔥 RTK 검증 실패 상세 정보 - 예외 스택 트레이스:", rtkException);
            }
        } else if (rtkException instanceof JwtMalformedException) {
            log.warn("⚠️ Refresh token validation failed: 토큰 형식 오류{}{}{}", 
                causeInfo, rtkTokenInfo, requestInfo, rtkException);
        } else if (rtkException instanceof JwtExpiredException) {
            log.warn("⚠️ Refresh token validation failed: 토큰 만료{}{}{}", 
                causeInfo, rtkTokenInfo, requestInfo, rtkException);
        } else if (rtkException instanceof JwtBlacklistException) {
            log.warn("⚠️ Refresh token validation failed: 블랙리스트 토큰{}{}{}", 
                causeInfo, rtkTokenInfo, requestInfo, rtkException);
        } else {
            log.warn("⚠️ Refresh token validation failed: 예상치 못한 예외 [{}] - {}{}{}{}", 
                rtkException.getClass().getSimpleName(), 
                rtkException.getMessage(), 
                causeInfo, rtkTokenInfo, requestInfo, rtkException);
        }
    }

    /**
     * RTK 검증 실패가 일시적인 실패(transient failure)인지 판단
     * - UUID 불일치: 동시 요청으로 인한 race condition 가능성 → transient
     * - 블랙리스트: rotate로 인한 블랙리스트 → transient
     * - 그 외: 진짜 문제 → non-transient
     */
    private boolean isTransientRtkFailure(Exception rtkException) {
        if (rtkException instanceof JwtBlacklistException) {
            // 블랙리스트는 rotate로 인한 것일 가능성이 높음
            return true;
        }
        
        if (rtkException instanceof JwtInvalidException) {
            // JwtInvalidException의 원인을 확인
            Throwable cause = rtkException.getCause();
            
            // cause가 null인 경우 - validateRtk에서 던진 예외
            // 로그 메시지를 통해 원인을 추론해야 함
            String message = rtkException.getMessage();
            if (message != null) {
                // UUID 불일치만 transient로 처리
                if (message.contains("RTK UUID 불일치") || message.contains("UUID 불일치")) {
                    return true;
                }
                // 그 외는 모두 non-transient (TokenType 불일치, Subject/RefreshUuid null, Redis 미존재 등)
            }
            
            // cause가 있는 경우도 UUID 불일치가 아닌 이상 non-transient
            // SecurityException, UnsupportedJwtException, IllegalArgumentException 등은 모두 non-transient
            return false;
        }
        
        // JwtExpiredException, JwtMalformedException 등은 모두 non-transient
        return false;
    }
}

