package com.capstone.passfolio.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "이메일 인증 코드 검증 — 발송과 동일하게 `SIGNUP`")
public record SystemAuthEmailVerifyRequest(
        @Schema(description = "이메일", example = "user@example.com") @NotBlank @Email String email,
        @Schema(description = "6자리 숫자 코드", example = "123456") @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code,
        @Schema(description = "발송 시와 동일하게 `SIGNUP`", example = "SIGNUP") @NotNull EmailPurpose purpose) {}
