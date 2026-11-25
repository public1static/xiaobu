package com.aegisid.foundation.shared.error;

/** Stable error definition owned by a bounded context. */
public interface BusinessErrorCode {

  ErrorCode errorCode();

  ErrorCategory category();
}
