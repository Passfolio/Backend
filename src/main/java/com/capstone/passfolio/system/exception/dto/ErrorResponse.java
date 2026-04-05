package com.capstone.passfolio.system.exception.dto;

import com.capstone.passfolio.system.exception.model.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@Builder
@Schema(description = "에러 응답 DTO")
public class ErrorResponse {
    @Schema(description = "HTTP 상태 코드", example = "BAD_REQUEST", type = "string", allowableValues = {
            "BAD_REQUEST", "UNAUTHORIZED", "FORBIDDEN", "NOT_FOUND", "CONFLICT",
            "TOO_MANY_REQUESTS", "INTERNAL_SERVER_ERROR"
    })
    public final HttpStatus status;
    
    @Schema(description = "에러 코드", example = "GLOBAL_INVALID_PARAMETER")
    public final String error;
    
    @Schema(description = "에러 메시지", example = "유효성 검사 실패: 필수 필드가 누락되었습니다.")
    public final String message;

    private ErrorResponse(ErrorCode errorCode) {
        this.status = errorCode.getStatus();
        this.error = errorCode.name();
        this.message = errorCode.getMessage();
    }

    private ErrorResponse(ErrorCode errorCode, String message) {
        this.status = errorCode.getStatus();
        this.error = errorCode.name();
        this.message = message;
    }

    private ErrorResponse(HttpStatus status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode, message);
    }

    public static ErrorResponse of(HttpStatus httpStatus, String error, String message) {
        return new ErrorResponse(httpStatus, error, message);
    }
}
