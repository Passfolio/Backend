package com.capstone.passfolio.system.exception.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Global
    GLOBAL_ALREADY_RESOURCE(HttpStatus.CONFLICT, "GLOBAL ALREADY RESOURCE", "이미 존재하는 자원입니다."),
    GLOBAL_BAD_REQUEST(HttpStatus.BAD_REQUEST, "GLOBAL BAD REQUEST", "잘못된 요청입니다."),
    GLOBAL_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "GLOBAL METHOD NOT ALLOWED", "허용되지 않는 메서드입니다."),
    GLOBAL_INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "GLOBAL INVALID PARAMETER", "필수 요청 파라미터가 누락되었습니다."),
    GLOBAL_INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GLOBAL INTERNAL SERVER ERROR", "서버 내부에 오류가 발생했습니다."),

    // JWT Errors
    JWT_INVALID(HttpStatus.UNAUTHORIZED, "JWT INVALID", "유효하지 않은 토큰입니다."),
    JWT_EXPIRED(HttpStatus.UNAUTHORIZED, "JWT EXPIRED", "만료된 토큰입니다."),
    JWT_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "JWT SESSION EXPIRED", "세션이 만료되었습니다. 다시 로그인해주세요."),
    JWT_NOT_FOUND(HttpStatus.UNAUTHORIZED, "JWT NOT FOUND", "인증 토큰을 찾을 수 없습니다."),
    JWT_MALFORMED(HttpStatus.UNAUTHORIZED, "JWT MALFORMED", "토큰 형식이 올바르지 않습니다."),
    JWT_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "JWT AUTHENTICATION FAILED", "토큰 인증에 실패했습니다."),
    JWT_CANNOT_GENERATE_TOKEN(HttpStatus.BAD_REQUEST, "JWT CANNOT GENERATE TOKEN", "토큰을 생성할 수 없습니다."),
    JWT_MISSING(HttpStatus.UNAUTHORIZED, "JWT MISSING", "토큰이 누락되었습니다."),
    JWT_FAILED_PARSING(HttpStatus.UNAUTHORIZED, "JWT FAILED PARSING", "토큰을 파싱하는데 실패했습니다."),
    JWT_BLACKLIST(HttpStatus.UNAUTHORIZED, "JWT BLACKLIST", "블랙리스트에 해당하는 토큰입니다."),

    // AUTH
    AUTH_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH USER NOT FOUND", "등록된 유저를 찾을 수 없습니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH FORBIDDEN", "접근 권한이 없습니다."),
    AUTH_EMAIL_NOT_ALLOWLISTED(
            HttpStatus.FORBIDDEN,
            "AUTH_EMAIL_NOT_ALLOWLISTED",
            "허용된 이메일이 아닙니다. 환경 변수 SYSTEM_AUTH_EMAIL_ALLOWLIST(쉼표 구분)에 등록된 주소만 사용할 수 있습니다."),
    AUTH_EMAIL_CODE_INVALID(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_CODE_INVALID", "이메일 인증코드가 올바르지 않거나 만료되었습니다."),
    AUTH_EMAIL_CODE_NOT_MATCHED(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_CODE_NOT_MATCHED", "이메일 인증코드가 일치하지 않습니다."),
    AUTH_EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "AUTH_EMAIL_NOT_VERIFIED", "이메일 인증이 완료되지 않았습니다."),
    AUTH_PASSWORD_NOT_MATCH(HttpStatus.UNAUTHORIZED, "AUTH_PASSWORD_NOT_MATCH", "비밀번호가 올바르지 않습니다."),
    AUTH_SYSTEM_USER_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH_SYSTEM_USER_REQUIRED", "시스템 로그인용 계정이 아닙니다. password가 설정된 사용자만 이용할 수 있습니다."),
    SYSTEM_AUTH_DISABLED(HttpStatus.NOT_FOUND, "SYSTEM_AUTH_DISABLED", "시스템 로그인이 비활성화되어 있습니다."),
    MAIL_CONNECTION_FAILED(HttpStatus.BAD_GATEWAY, "MAIL_CONNECTION_FAILED", "메일 서버에 연결할 수 없습니다."),

    // OAUTH
    OAUTH_BAD_REQUEST(HttpStatus.BAD_REQUEST, "OAUTH BAD REQUEST", "OAUTH에 대해 잘못된 요청입니다."),
    OAUTH_USER_ALREADY_EXIST(HttpStatus.CONFLICT, "OAUTH_USER_ALREADY_EXIST", "이미 다른 소셜/일반 유저로 존재하는 사용자입니다."),

    // USER
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER NOT FOUND", "존재하지 않는 사용자입니다."),
    USER_USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER USERNAME ALREADY EXISTS", "중복되는 아이디입니다."),
    USER_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "USER UNAUTHORIZED", "인증되지 않은 유저입니다."),

    // REDIS Errors
    REDIS_CONNECTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "REDIS_CONNECTION_FAILED", "Redis 서버에 연결할 수 없습니다."),
    REDIS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "REDIS_ERROR", "Redis 처리 중 오류가 발생했습니다."),
    REDIS_COMMAND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "REDIS_COMMAND_FAILED", "Redis 명령 실행 중 오류가 발생했습니다."),
    REDIS_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "REDIS_TIMEOUT", "Redis 서버 응답 시간이 초과되었습니다."),

    // FILE Errors
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "FILE NOT FOUND", "파일을 찾을 수 없습니다."),
    FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "FILE STORAGE ERROR", "파일 저장 중 오류가 발생했습니다."),
    FILE_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "FILE INVALID FORMAT", "유효하지 않은 파일 형식입니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "FILE SIZE EXCEEDED", "파일 크기가 허용된 한도를 초과했습니다."),
    FILE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FILE ACCESS DENIED", "파일에 접근할 수 있는 권한이 없습니다."),

    // PAGE Errors
    PAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "PAGE NOT FOUND", "존재하지 않는 페이지입니다."),

    // DB Errors
    DB_CONNECTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "DB CONNECTION FAILED", "데이터베이스 연결에 실패했습니다."),
    DB_QUERY_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "DB QUERY TIMEOUT", "쿼리 실행 시간이 초과되었습니다."),
    DB_DEADLOCK(HttpStatus.CONFLICT, "DB DEADLOCK", "데드락이 발생했습니다."),
    DB_OPTIMISTIC_LOCK_FAILED(HttpStatus.CONFLICT, "DB OPTIMISTIC LOCK FAILED", "낙관적 락 실패"),
    DB_PESSIMISTIC_LOCK_FAILED(HttpStatus.CONFLICT, "DB PESSIMISTIC LOCK FAILED", "비관적 락 실패"),
    DB_INCORRECT_RESULT_SIZE(HttpStatus.INTERNAL_SERVER_ERROR, "DB INCORRECT RESULT SIZE", "결과 크기 불일치"),
    DB_TRANSACTION_SERIALIZATION_FAILED(HttpStatus.CONFLICT, "DB TRANSACTION SERIALIZATION FAILED", "트랜잭션 직렬화 실패"),
    DB_DATA_ACCESS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "DB DATA ACCESS ERROR", "데이터 접근 오류"),
    DB_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "DB DATA NOT FOUND", "존재하지 않는 데이터입니다."),
    DB_DATA_TOO_LONG(HttpStatus.BAD_REQUEST, "DB DATA TOO LONG", "데이터 길이가 허용된 한도를 초과했습니다."),
    DB_NOT_NULL_VIOLATION(HttpStatus.BAD_REQUEST, "DB NOT NULL VIOLATION", "필수 필드가 누락되었습니다."),
    DB_FOREIGN_KEY_VIOLATION(HttpStatus.BAD_REQUEST, "DB FOREIGN KEY VIOLATION", "참조 무결성 제약 조건을 위반했습니다."),

    // GITHUB Errors
    GITHUB_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "GITHUB_TOKEN_NOT_FOUND", "GitHub 연동 토큰이 없습니다. GitHub으로 다시 로그인해주세요."),
    GITHUB_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "GITHUB_TOKEN_EXPIRED", "GitHub 토큰이 만료되었습니다. GitHub으로 다시 로그인해주세요."),
    GITHUB_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "GITHUB_RATE_LIMITED", "GitHub API 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."),
    GITHUB_API_ERROR(HttpStatus.BAD_GATEWAY, "GITHUB_API_ERROR", "GitHub API 호출 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String error;
    private final String message;
}