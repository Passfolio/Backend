package com.capstone.passfolio.system.security.jwt.exception;

import com.capstone.passfolio.system.exception.model.ErrorCode;

public class JwtParseException extends JwtRestException {
    public JwtParseException() {
        super(ErrorCode.JWT_FAILED_PARSING);
    }

    public JwtParseException(String message) {
        super(ErrorCode.JWT_FAILED_PARSING, message);
    }

    public JwtParseException(Throwable cause) {
        super(ErrorCode.JWT_FAILED_PARSING, cause);
    }
}
