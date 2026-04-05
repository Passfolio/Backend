package com.capstone.passfolio.system.security.jwt.exception;

import com.capstone.passfolio.system.exception.model.ErrorCode;

public class JwtMissingException extends JwtRestException {
    public JwtMissingException() {
        super(ErrorCode.JWT_MISSING);
    }

    public JwtMissingException(String message) {
        super(ErrorCode.JWT_MISSING, message);
    }

    public JwtMissingException(Throwable cause) {
        super(ErrorCode.JWT_MISSING, cause);
    }
}
