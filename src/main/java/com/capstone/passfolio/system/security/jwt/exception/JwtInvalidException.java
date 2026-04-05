package com.capstone.passfolio.system.security.jwt.exception;

import com.capstone.passfolio.system.exception.model.ErrorCode;

public class JwtInvalidException extends JwtRestException {
    public JwtInvalidException() {
        super(ErrorCode.JWT_INVALID);
    }

    public JwtInvalidException(String message) {
        super(ErrorCode.JWT_INVALID, message);
    }

    public JwtInvalidException(Throwable cause) {
        super(ErrorCode.JWT_INVALID, cause);
    }
}
