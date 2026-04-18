package com.capstone.passfolio.domain.auth.dto;

import com.capstone.passfolio.domain.user.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "시스템 로그인 계정 가입 (allowlist 이메일만)")
public record SystemAuthSignupRequest(
        @Schema(description = "가입 이메일 (= 이후 username)", example = "dev@example.com") @NotBlank @Email String email,
        @Schema(description = "평문 비밀번호 (BCrypt 저장)", example = "********") @NotBlank @Size(min = 8, max = 72) String password,
        @Schema(description = "비어 있으면 이메일 @ 앞부분을 닉네임으로 사용") String nickname,
        @Schema(description = "권한 — `USER` 또는 `ADMIN`", example = "USER") @NotNull Role role) {}
