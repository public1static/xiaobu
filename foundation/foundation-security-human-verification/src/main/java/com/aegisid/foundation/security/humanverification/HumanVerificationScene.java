package com.aegisid.foundation.security.humanverification;

public enum HumanVerificationScene {
  SIGN_IN,
  SIGN_UP,
  LOGIN_SUBMIT,
  REGISTER_SUBMIT,
  OTP_DISPATCH,
  PASSWORD_RESET,
  SENSITIVE_ACTION;

  public HumanVerificationScene canonical() {
    return switch (this) {
      case LOGIN_SUBMIT -> SIGN_IN;
      case REGISTER_SUBMIT -> SIGN_UP;
      default -> this;
    };
  }
}
