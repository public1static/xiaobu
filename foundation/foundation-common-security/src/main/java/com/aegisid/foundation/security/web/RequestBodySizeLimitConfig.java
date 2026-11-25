package com.aegisid.foundation.security.web;

import java.util.Locale;

public record RequestBodySizeLimitConfig(long requestBodyMaxBytes, long multipartMaxBytes) {

  private static final long DEFAULT_REQUEST_BODY_MAX_BYTES = 1024L * 1024L;
  private static final long DEFAULT_MULTIPART_MAX_BYTES = 3L * 1024L * 1024L;

  public RequestBodySizeLimitConfig {
    if (requestBodyMaxBytes < 1) {
      throw new IllegalArgumentException("requestBodyMaxBytes must be positive");
    }
    if (multipartMaxBytes < 1) {
      throw new IllegalArgumentException("multipartMaxBytes must be positive");
    }
  }

  public static RequestBodySizeLimitConfig defaults() {
    return new RequestBodySizeLimitConfig(
        DEFAULT_REQUEST_BODY_MAX_BYTES, DEFAULT_MULTIPART_MAX_BYTES);
  }

  long limitFor(String contentType) {
    return isMultipart(contentType) ? multipartMaxBytes : requestBodyMaxBytes;
  }

  static boolean isMultipart(String contentType) {
    return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
  }
}
