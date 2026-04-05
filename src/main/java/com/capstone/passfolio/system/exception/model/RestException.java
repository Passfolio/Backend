package com.capstone.passfolio.system.exception.model;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@Slf4j
@Getter
public class RestException extends RuntimeException {
    private final ErrorCode errorCode;

    public RestException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public RestException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause); // With Cause
        this.errorCode = errorCode;
    }

    public RestException(ErrorCode errorCode, String message) {
        super(message); // Custom Message
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return errorCode.getStatus();
    }

    public String getError() {
        return errorCode.getError();
    }
}