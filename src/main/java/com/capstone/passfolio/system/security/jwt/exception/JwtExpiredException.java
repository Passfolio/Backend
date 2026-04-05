package com.capstone.passfolio.system.security.jwt.exception;

import com.capstone.passfolio.system.exception.model.ErrorCode;

public class JwtExpiredException extends JwtRestException {
    public JwtExpiredException() {
        super(ErrorCode.JWT_EXPIRED);
    }

    public JwtExpiredException(String message) {
        super(ErrorCode.JWT_EXPIRED, message);
    }

    public JwtExpiredException(Throwable cause) {
        super(ErrorCode.JWT_EXPIRED, cause);
    }
}
