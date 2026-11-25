package com.aegisid.foundation.web.api;

import com.aegisid.foundation.web.resource.ApiResources;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standard API error envelope.
 *
 * @param success success marker, always false for this type
 * @param code stable machine-readable error code
 * @param message human-readable error message
 * @param data response payload, always null for errors
 * @param details optional structured details owned by the caller
 * @param resources display resources, always empty for direct errors
 * @param requestId request correlation id
 * @param timestamp response creation time
 */
public record ApiErrorResponse(
    boolean success,
    String code,
    String message,
    Object data,
    Map<String, Object> details,
    ApiResources resources,
    String requestId,
    Instant timestamp) {

  public ApiErrorResponse {
    success = false;
    data = null;
    details = details == null ? Map.of() : Map.copyOf(details);
    resources = ApiResources.copyOf(resources);
  }

  public ApiErrorResponse(
      boolean success,
      String code,
      String message,
      String requestId,
      Instant timestamp,
      Map<String, Object> details) {
    this(success, code, message, null, details, ApiResources.empty(), requestId, timestamp);
  }

  public static ApiErrorResponse of(
      String code, String message, Map<String, ?> details, String requestId, Instant timestamp) {
    return new ApiErrorResponse(
        false,
        code,
        message,
        null,
        normalizeDetails(details),
        ApiResources.empty(),
        requestId,
        timestamp);
  }

  public static ApiErrorResponse failure(
      String code, String message, String requestId, Map<String, ?> details) {
    return of(code, message, details, requestId, Instant.now());
  }

  private static Map<String, Object> normalizeDetails(Map<String, ?> details) {
    if (details == null) {
      return Map.of();
    }
    Map<String, Object> normalized = new LinkedHashMap<>();
    details.forEach(normalized::put);
    return normalized;
  }
}
