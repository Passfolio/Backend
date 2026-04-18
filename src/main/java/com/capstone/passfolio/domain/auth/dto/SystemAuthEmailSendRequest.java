package com.capstone.passfolio.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "이메일 인증 코드 발송 — 시스템 로그인용 아님, 회원가입(SIGNUP)만 지원")
public record SystemAuthEmailSendRequest(
        @Schema(description = "이메일 (= username)", example = "user@example.com") @NotBlank @Email String email,
        @Schema(description = "반드시 `SIGNUP` (비밀번호 재설정 등은 미구현)", example = "SIGNUP") @NotNull EmailPurpose purpose) {}
