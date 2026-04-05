package com.capstone.passfolio.system.security.jwt.exception;

import com.capstone.passfolio.system.exception.model.ErrorCode;

public class JwtBlacklistException extends JwtRestException {
    public JwtBlacklistException() {
        super(ErrorCode.JWT_BLACKLIST);
    }

    public JwtBlacklistException(String message) {
        super(ErrorCode.JWT_BLACKLIST, message);
    }

    public JwtBlacklistException(Throwable cause) {
        super(ErrorCode.JWT_BLACKLIST, cause);
    }
}
