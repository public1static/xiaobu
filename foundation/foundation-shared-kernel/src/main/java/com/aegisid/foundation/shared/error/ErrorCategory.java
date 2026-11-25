package com.aegisid.foundation.shared.error;

/** Transport-neutral classification for stable application errors. */
public enum ErrorCategory {
  INVALID_ARGUMENT,
  UNAUTHENTICATED,
  FORBIDDEN,
  NOT_FOUND,
  CONFLICT,
  RULE_VIOLATION,
  RATE_LIMITED,
  UPSTREAM_FAILURE,
  SERVICE_UNAVAILABLE,
  INTERNAL
}
