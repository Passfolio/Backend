package com.capstone.passfolio.domain.user.controller;

import com.capstone.passfolio.domain.user.dto.UserDto;
import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import com.capstone.passfolio.system.security.model.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;

@Tag(
        name = "User",
        description ="로그인 사용자 본인 정보 API")
@SecurityRequirement(name = "bearerAuth")
public interface UserApiSpecification {

    @Operation(
            summary = "내 정보 조회",
            description =
                    """
                    현재 인증된 사용자 ID로 DB를 조회해 프로필 요약을 반환합니다.

                    ## 사전 조건
                    - 유효한 액세스 토큰(쿠키)과 `UserPrincipal`이 필요합니다.

                    ## 처리 흐름
                    1. `userPrincipal.getUserId()`로 PK 조회
                    2. 없으면 `USER_NOT_FOUND` (`RestException`)
                    3. `UserDto.UserResponse.from`으로 응답 매핑

                    ## 응답 필드 참고
                    - `githubLogin`: GitHub 연동 시 저장된 로그인명 (없으면 null)
                    - `lastModifiedAt`: 응답 생성 시각 기준으로 채워질 수 있습니다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "내 정보 조회 성공",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = UserDto.UserResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "성공 응답",
                                                        value =
                                                                """
                                                                {
                                                                  "id": 1,
                                                                  "role": "USER",
                                                                  "nickname": "hades",
                                                                  "profileImageUrl": "https://avatars.githubusercontent.com/u/12345678?v=4",
                                                                  "githubLogin": "Youcu",
                                                                  "createdAt": "2026-04-15T01:00:00.000000Z",
                                                                  "lastModifiedAt": "2026-04-15T01:30:00.000000Z"
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "401",
                        description =
                                """
                                인증 실패 (필터·엔트리포인트)

                                | ErrorCode | 상황 |
                                |-----------|------|
                                | `JWT_MISSING` | 토큰 쿠키 없음 |
                                | `JWT_EXPIRED` | 만료된 토큰 |
                                | `JWT_INVALID` | 유효하지 않은 토큰 |
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
                        responseCode = "403",
                        description = "인증은 되었으나 역할 등으로 접근 거부",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "AUTH_FORBIDDEN",
                                                        value =
                                                                """
                                                                {
                                                                  "status": "FORBIDDEN",
                                                                  "error": "AUTH_FORBIDDEN",
                                                                  "message": "접근 권한이 없습니다."
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "404",
                        description = "DB에 사용자 행이 없음 (데이터 불일치)",
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
    UserDto.UserResponse retrieve(@Parameter(hidden = true) UserPrincipal userPrincipal);
}
