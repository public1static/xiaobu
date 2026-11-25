package com.aegisid.foundation.security.humanverification;

public interface HumanVerificationRequestContext {
  Object sessionAttribute(String name);

  void setSessionAttribute(String name, Object value);
}
