package com.capstone.passfolio.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "시스템 로그인 요청 (username = DB의 username, 이메일 주소)")
public record SystemAuthLoginRequest(
        @Schema(description = "DB username 컬럼 값(이메일)", example = "admin@example.com")
        @NotBlank @Email String username,
        @Schema(description = "평문 비밀번호") @NotBlank String password,
        @Schema(description = "리프레시 토큰 장기 보관 여부", example = "false") boolean rememberMe) {}
