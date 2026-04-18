package com.capstone.passfolio.system.controller;

import com.capstone.passfolio.domain.auth.dto.SystemAuthEmailSendRequest;
import com.capstone.passfolio.domain.auth.dto.SystemAuthEmailVerifyRequest;
import com.capstone.passfolio.domain.auth.dto.SystemAuthLoginRequest;
import com.capstone.passfolio.domain.auth.dto.SystemAuthSignupRequest;
import com.capstone.passfolio.system.exception.dto.ErrorResponse;
import com.capstone.passfolio.system.security.jwt.dto.JwtDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "System — Auth (email + password)")
// @Hidden
public interface SystemAuthApiSpecification {

    @Operation(
            // hidden = true,
            summary = "회원가입 완료",
            description =
                    """
                    DEPth `POST /api/v1/auth/register`와 같이 **이메일 인증이 끝난 뒤** 호출합니다.

                    ## 선행 조건
                    - `POST .../email/send` + `POST .../email/verify` 를 **purpose=`SIGNUP`** 으로 완료해 Redis에 `signup:verified` 가 있어야 합니다.

                    ## 동작
                    - 요청 본문 `role`에 **`USER` 또는 `ADMIN`** 지정 (검증: `@NotNull`).
                    - allowlist·`hasVerifiedForSignup` 검사 후 사용자 저장, 가입 플래그 삭제.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "201", description = "가입 완료"),
                @ApiResponse(
                        responseCode = "401",
                        description = "이메일 인증 미완료",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "allowlist 아님",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "이미 존재하는 username",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    ResponseEntity<Void> signup(@Valid SystemAuthSignupRequest request);

    @Operation(
            // hidden = true,
            summary = "이메일 인증 코드 발송 (회원가입 전용)",
            description =
                    """
                    **회원가입** 절차에서만 사용합니다. `purpose`는 **`SIGNUP`만** 허용합니다.

                    - 해당 이메일이 **아직 DB에 없어야** 함 (있으면 409).
                    - 시스템 로그인은 이메일 인증 없이 `login`만 호출하면 됩니다.

                    공통: allowlist, 코드 Redis 5분 TTL.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", description = "발송 완료"),
                @ApiResponse(
                        responseCode = "400",
                        description = "purpose가 SIGNUP이 아님",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "allowlist 아님",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "이미 가입됨",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "502",
                        description = "메일 서버 오류",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    ResponseEntity<Void> sendEmailVerification(@Valid SystemAuthEmailSendRequest request);

    @Operation(
            // hidden = true,
            summary = "이메일 인증 코드 검증 (회원가입 전용)",
            description =
                    """
                    `email`, `code`, **`purpose`** (`SIGNUP`만). 검증 성공 시 Redis `signup:verified` (TTL 15분).
                    발송 시와 동일하게 `SIGNUP`을 넣습니다.
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "204", description = "검증 성공"),
                @ApiResponse(
                        responseCode = "400",
                        description = "코드 오류 또는 미지원 purpose",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "코드 불일치",
                                                        value =
                                                                """
                                                                {
                                                                  "status": 400,
                                                                  "error": "AUTH_EMAIL_CODE_NOT_MATCHED",
                                                                  "message": "이메일 인증코드가 일치하지 않습니다."
                                                                }
                                                                """))),
                @ApiResponse(
                        responseCode = "403",
                        description = "allowlist 아님",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "SIGNUP 시 이미 가입됨",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    ResponseEntity<Void> verifyEmail(@Valid SystemAuthEmailVerifyRequest request);

    @Operation(
            // hidden = true,
            summary = "시스템 로그인",
            description =
                    """
                    이메일·비밀번호만으로 로그인합니다. **별도 이메일 인증 단계 없음** (가입 시 이미 인증됨).
                    """)
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "로그인 성공",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = JwtDto.TokenExpiresInfo.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "비밀번호 불일치",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    ResponseEntity<JwtDto.TokenExpiresInfo> login(
            @Valid SystemAuthLoginRequest request, HttpServletResponse response);
}
