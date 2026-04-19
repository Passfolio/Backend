package com.capstone.passfolio.domain.auth.dto;

public enum EmailPurpose {
    /** 회원가입 전용 — 시스템 로그인은 회원가입에서 이미 이메일 인증됨 */
    SIGNUP,
    PASSWORD_RESET
}
