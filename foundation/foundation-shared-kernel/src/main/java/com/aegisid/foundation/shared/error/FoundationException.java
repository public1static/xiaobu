package com.aegisid.foundation.shared.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Base runtime exception for stable application failures. */
public class FoundationException extends RuntimeException {

  private final ErrorCode errorCode;
  private final ErrorCategory category;
  private final Map<String, Object> details;

  public FoundationException(ErrorCode errorCode, String message) {
    this(errorCode, ErrorCategory.INVALID_ARGUMENT, message, Map.of(), null);
  }

  public FoundationException(ErrorCode errorCode, String message, Throwable cause) {
    this(errorCode, ErrorCategory.INVALID_ARGUMENT, message, Map.of(), cause);
  }

  public FoundationException(
      ErrorCode errorCode, ErrorCategory category, String message, Map<String, ?> details) {
    this(errorCode, category, message, details, null);
  }

  public FoundationException(
      ErrorCode errorCode,
      ErrorCategory category,
      String message,
      Map<String, ?> details,
      Throwable cause) {
    super(message, cause);
    this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    this.category = Objects.requireNonNull(category, "category must not be null");
    this.details = immutableDetails(details);
  }

  protected FoundationException(BusinessErrorCode error, String message) {
    this(error, message, Map.of(), null);
  }

  protected FoundationException(
      BusinessErrorCode error, String message, Map<String, ?> details, Throwable cause) {
    this(
        Objects.requireNonNull(error, "error must not be null").errorCode(),
        error.category(),
        message,
        details,
        cause);
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public ErrorCategory category() {
    return category;
  }

  public Map<String, Object> details() {
    return details;
  }

  private static Map<String, Object> immutableDetails(Map<String, ?> details) {
    Objects.requireNonNull(details, "details must not be null");
    Map<String, Object> copy = new LinkedHashMap<>();
    details.forEach(
        (key, value) ->
            copy.put(
                Objects.requireNonNull(key, "detail key must not be null"),
                Objects.requireNonNull(value, "detail value must not be null")));
    return Collections.unmodifiableMap(copy);
  }
}
