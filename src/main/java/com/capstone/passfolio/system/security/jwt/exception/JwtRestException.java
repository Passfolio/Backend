package com.capstone.passfolio.system.security.jwt.exception;

import com.capstone.passfolio.system.exception.model.ErrorCode;
import com.capstone.passfolio.system.exception.model.RestException;
import lombok.Getter;

@Getter
public class JwtRestException extends RestException {
    public JwtRestException(ErrorCode errorCode) {
        super(errorCode);
    }

    public JwtRestException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public JwtRestException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
