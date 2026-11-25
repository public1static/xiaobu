package com.aegisid.foundation.shared.error;

/**
 * Stable machine-readable error code.
 *
 * @param value stable error code value
 */
public record ErrorCode(String value) {

  public ErrorCode {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("ErrorCode value must not be blank");
    }
  }
}
