package com.capstone.passfolio.system.security.jwt.exception;

import com.capstone.passfolio.system.exception.model.ErrorCode;

public class JwtAuthenticationException extends JwtRestException {
  public JwtAuthenticationException() {
    super(ErrorCode.JWT_AUTHENTICATION_FAILED);
  }

  public JwtAuthenticationException(String message) {
    super(ErrorCode.JWT_AUTHENTICATION_FAILED, message);
  }

  public JwtAuthenticationException(Throwable cause) {
    super(ErrorCode.JWT_AUTHENTICATION_FAILED, cause);
  }
}
