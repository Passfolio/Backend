package com.capstone.passfolio.domain.auth.controller;

import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import com.capstone.passfolio.system.security.jwt.dto.JwtDto;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(
        name = "Auth",
        description = "인증·세션·토큰 갱신 API")
public interface AuthApiSpecification {

    @Operation(
            summary = "로그아웃",
            description =
                    """
                    ATK·RTK를 검증한 뒤 Redis 블랙리스트에 등록하고, 응답 쿠키를 제거합니다.

                    ## 사전 조건
                    - 인증된 세션(유효한 ATK·RTK 쿠키)이 필요합니다.

                    ## 처리 흐름
                    1. 요청에서 ATK·RTK 문자열 추출 (`JwtTokenResolver`)
                    2. `TokenService.clearTokensByAtkWithValidation`로 서버 측 토큰 무효화
                    3. 액세스·리프레시 토큰 쿠키 삭제

                    ## 주의사항
                    - ATK 또는 RTK 중 하나라도 쿠키에 없으면 `JWT_MISSING`이 발생할 수 있습니다.
                    - 토큰 파싱·검증 실패 시 JWT 계열 `ErrorCode`가 반환될 수 있습니다.
                    """)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "로그아웃 성공",
                        content =
                                @Content(
                                        mediaType = MediaType.TEXT_PLAIN_VALUE,
                                        examples = @ExampleObject(name = "성공", value = "Logout Successful"))),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                """
                                인증 실패

                                | ErrorCode | 상황 | 해결 방법 |
                                |-----------|------|----------|
                                | `JWT_MISSING` | ATK/RTK 쿠키 누락 | 로그인 후 재시도 |
                                | `JWT_EXPIRED` | 만료된 토큰 | 리프레시 또는 재로그인 |
                                | `JWT_INVALID` | 유효하지 않은 토큰 | 재로그인 |
                                | `JWT_BLACKLIST` | 블랙리스트 토큰 | 재로그인 |
                                """,
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples = {
                                            @ExampleObject(
                                                    name = "JWT_MISSING",
                                                    value =
                                                            """
                                                            {
                                                              "status": "UNAUTHORIZED",
                                                              "error": "JWT_MISSING",
                                                              "message": "토큰이 누락되었습니다."
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "JWT_EXPIRED",
                                                    value =
                                                            """
                                                            {
                                                              "status": "UNAUTHORIZED",
                                                              "error": "JWT_EXPIRED",
                                                              "message": "만료된 토큰입니다."
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "JWT_INVALID",
                                                    value =
                                                            """
                                                            {
                                                              "status": "UNAUTHORIZED",
                                                              "error": "JWT_INVALID",
                                                              "message": "유효하지 않은 토큰입니다."
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "JWT_BLACKLIST",
                                                    value =
                                                            """
                                                            {
                                                              "status": "UNAUTHORIZED",
                                                              "error": "JWT_BLACKLIST",
                                                              "message": "블랙리스트에 해당하는 토큰입니다."
                                                            }
                                                            """)
                                        }))
            })
    ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response);

    @Operation(
            summary = "세션 종료(GitHub OAuth 토큰 폐기)",
            description =
                    """
                    현재 JWT를 무효화하고, Redis에 캐시된 GitHub OAuth 액세스 토큰을 폐기합니다.
                    다음 로그인 시 GitHub OAuth를 다시 연결해야 합니다.

                    ## 처리 흐름
                    1. Redis에서 암호화된 GitHub access token 조회 → 복호화 후 GitHub grant DELETE (best-effort)
                    2. ATK·RTK 블랙리스트 등록
                    3. 쿠키 삭제
                    """)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "세션 종료 성공",
                content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
                        examples = @ExampleObject(name = "성공", value = "Session Revoked"))),
        @ApiResponse(responseCode = "401", description = "인증 실패 (JWT 계열)",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<String> revokeSession(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            HttpServletRequest request,
            HttpServletResponse response);

    @Operation(
            summary = "회원 탈퇴",
            description =
                    """
                    현재 로그인된 사용자 행을 삭제하고, 토큰을 무효화한 뒤 쿠키를 제거합니다.

                    ## 사전 조건
                    - 인증된 `UserPrincipal`과 유효한 ATK·RTK 쿠키가 필요합니다.

                    ## 처리 흐름
                    1. DB에서 사용자 조회 → 없으면 `USER_NOT_FOUND`
                    2. ATK·RTK 검증 후 서버 측 토큰 정리 및 쿠키 삭제
                    3. `userRepository.deleteByUserId`로 계정 삭제

                    ## 주의사항
                    - **Hard delete**이며 복구되지 않습니다.
                    """)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "회원 탈퇴 성공",
                        content =
                                @Content(
                                        mediaType = MediaType.TEXT_PLAIN_VALUE,
                                        examples =
                                                @ExampleObject(name = "성공", value = "Soft Delete User Successful"))),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                """
                                인증 실패 (JWT 계열)

                                | ErrorCode | 상황 |
                                |-----------|------|
                                | `JWT_MISSING` | ATK/RTK 쿠키 누락 |
                                | `JWT_EXPIRED` / `JWT_INVALID` / `JWT_BLACKLIST` | 토큰 상태 이상 |
                                """,
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples = {
                                            @ExampleObject(
                                                    name = "JWT_MISSING",
                                                    value =
                                                            """
                                                            {
                                                              "status": "UNAUTHORIZED",
                                                              "error": "JWT_MISSING",
                                                              "message": "토큰이 누락되었습니다."
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "JWT_EXPIRED",
                                                    value =
                                                            """
                                                            {
                                                              "status": "UNAUTHORIZED",
                                                              "error": "JWT_EXPIRED",
                                                              "message": "만료된 토큰입니다."
                                                            }
                                                            """)
                                        })),
                @ApiResponse(
                        responseCode = "404",
                        description = "사용자를 찾을 수 없습니다.",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "USER_NOT_FOUND",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "NOT_FOUND",
                                                                  "error": "USER_NOT_FOUND",
                                                                  "message": "존재하지 않는 사용자입니다."
                                                                }
                                                                """)))
            })
    ResponseEntity<String> delete(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            HttpServletRequest request,
            HttpServletResponse response);

    @Operation(
            summary = "리프레시 토큰",
            description =
                    """
                    리프레시 토큰 쿠키를 검증·회전(rotate)하고, 새 ATK·RTK를 **HttpOnly 쿠키**로 내려줍니다.

                    ## 인증
                    - **비인증(permitAll)** 엔드포인트입니다. (Security 설정 기준)

                    ## 처리 흐름 (요약)
                    1. 쿠키에서 RTK 추출 → 없으면 `JWT_MISSING`
                    2. RTK 페이로드 검증, Redis 허용 UUID·블랙리스트·락 경쟁 조건 처리
                    3. 새 토큰 페어 발급 및 쿠키 재설정
                    4. 응답 본문에는 **만료 시각만** 포함 (`TokenExpiresInfo`)

                    ## 쿼리 파라미터
                    - `rememberMe`: 소셜 자동 로그인 등에서 사용하는 **장기 세션 플래그**입니다.

                    ## 주의사항
                    - 동시 갱신·Grace period·UUID 불일치 시 `JWT_INVALID` 등이 발생할 수 있습니다.
                    - 락 대기 중 인터럽트 시 `GLOBAL_INTERNAL_SERVER_ERROR`가 발생할 수 있습니다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "리프레시 성공 — 새 토큰은 Set-Cookie로 전달",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = JwtDto.TokenExpiresInfo.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "만료 시각 응답",
                                                        value =
                                                                """
                                                                {
                                                                  "accessTokenExpiresAt": "2026-04-15T05:00:00.000000Z",
                                                                  "refreshTokenExpiresAt": "2026-04-20T05:00:00.000000Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                """
                                리프레시 실패

                                | ErrorCode | 상황 |
                                |-----------|------|
                                | `JWT_MISSING` | RTK 쿠키 없음 |
                                | `JWT_EXPIRED` | 만료된 RTK |
                                | `JWT_INVALID` | RTK 형식/UUID/세션 불일치 등 |
                                | `JWT_BLACKLIST` | 블랙리스트 RTK |
                                | `JWT_AUTHENTICATION_FAILED` | 토큰 인증 실패 |
                                """,
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples = {
                                            @ExampleObject(
                                                    name = "JWT_MISSING",
                                                    value =
                                                            """
                                                            {
                                                              "status": "UNAUTHORIZED",
                                                              "error": "JWT_MISSING",
                                                              "message": "토큰이 누락되었습니다."
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "JWT_EXPIRED",
                                                    value =
                                                            """
                                                            {
                                                              "status": "UNAUTHORIZED",
                                                              "error": "JWT_EXPIRED",
                                                              "message": "만료된 토큰입니다."
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "JWT_INVALID",
                                                    value =
                                                            """
                                                            {
                                                              "status": "UNAUTHORIZED",
                                                              "error": "JWT_INVALID",
                                                              "message": "유효하지 않은 토큰입니다."
                                                            }
                                                            """),
                                            @ExampleObject(
                                                    name = "JWT_BLACKLIST",
                                                    value =
                                                            """
                                                            {
                                                              "status": "UNAUTHORIZED",
                                                              "error": "JWT_BLACKLIST",
                                                              "message": "블랙리스트에 해당하는 토큰입니다."
                                                            }
                                                            """)
                                        })),
                @ApiResponse(
                        responseCode = "500",
                        description = "락 처리 등 서버 내부 오류",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "GLOBAL_INTERNAL_SERVER_ERROR",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "INTERNAL_SERVER_ERROR",
                                                                  "error": "GLOBAL_INTERNAL_SERVER_ERROR",
                                                                  "message": "서버 내부에 오류가 발생했습니다."
                                                                }
                                                                """)))
            })
    ResponseEntity<JwtDto.TokenExpiresInfo> refresh(
            @Parameter(
                    description =
                            """
                            로그인 유지(Remember-Me) 여부.
                            소셜 자동 로그인 시나리오에서 RTK TTL·쿠키 정책에 반영됩니다.
                            """,
                    example = "false")
                    boolean rememberMe,
            HttpServletRequest request,
            HttpServletResponse response);

    @Hidden
    @Operation(
            summary = "(내부) 리프레시 토큰 블랙리스트 여부",
            description =
                    """
                    전달한 RTK 문자열의 `refreshUuid`가 Redis 블랙리스트에 있는지 확인합니다.

                    ## 인증
                    - **비인증(permitAll)** 운영·점검용입니다.

                    ## 주의
                    - 잘못된 JWT 문자열이면 파싱 단계에서 예외가 발생할 수 있습니다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "블랙리스트 여부",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = Boolean.class),
                                        examples = {
                                            @ExampleObject(name = "블랙리스트됨", value = "true"),
                                            @ExampleObject(name = "정상", value = "false")
                                        }))
            })
    boolean isRtkBlacklisted(
            @Parameter(
                    name = "refreshToken",
                    description = "검사할 리프레시 토큰 원문",
                    required = true,
                    example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                    String refreshToken);

    @Hidden
    @Operation(
            summary = "(내부) 액세스 토큰 블랙리스트 여부",
            description =
                    """
                    전달한 ATK의 `jti`가 Redis 블랙리스트에 있는지 확인합니다.

                    ## 인증
                    - **비인증(permitAll)** 운영·점검용입니다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "블랙리스트 여부",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = Boolean.class),
                                        examples = {
                                            @ExampleObject(name = "블랙리스트됨", value = "true"),
                                            @ExampleObject(name = "정상", value = "false")
                                        }))
            })
    boolean isAtkBlacklisted(
            @Parameter(
                    name = "accessToken",
                    description = "검사할 액세스 토큰 원문",
                    required = true,
                    example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                    String accessToken);

    @Operation(
            summary = "TokenPair 유효성 검증",
            description =
                    """
                    요청 쿠키의 ATK·RTK를 추출해 페어 검증에 성공하면 `true`, 예외·불일치 시 `false`를 반환합니다.

                    ## 사전 조건
                    - 인증 필터를 통과한 요청이어야 합니다. (미인증 시 보통 401 `JWT_MISSING`)

                    ## 반환값
                    - **200** 항상 JSON boolean (`true` / `false`). 오류 시에도 예외를 던지지 않고 `false`로 떨어질 수 있습니다.
                    """)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "검증 결과",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = Boolean.class),
                                        examples = {
                                            @ExampleObject(name = "유효한 토큰 페어", value = "true"),
                                            @ExampleObject(name = "무효·불일치", value = "false")
                                        }))
            })
    boolean isTokenActive(HttpServletRequest request);
}
