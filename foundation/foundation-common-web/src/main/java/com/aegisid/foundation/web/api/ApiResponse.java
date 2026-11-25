package com.aegisid.foundation.web.api;

import com.aegisid.foundation.shared.request.RequestContextView;
import com.aegisid.foundation.web.request.RequestContext;
import com.aegisid.foundation.web.resource.ApiResources;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Standard successful API envelope.
 *
 * @param success success marker
 * @param code stable machine-readable code
 * @param message human-readable message
 * @param data response payload
 * @param resources display resources used by the response payload
 * @param requestId request correlation id
 * @param timestamp response creation time
 * @param <T> payload type
 */
public record ApiResponse<T>(
    boolean success,
    String code,
    String message,
    T data,
    ApiResources resources,
    String requestId,
    Instant timestamp) {

  public static final String SUCCESS_CODE = "OK";

  public static final String SUCCESS_MESSAGE = "success";

  public static final String OK_MESSAGE = "OK";

  public ApiResponse {
    resources = ApiResources.copyOf(resources);
  }

  public ApiResponse(
      boolean success, String code, String message, T data, String requestId, Instant timestamp) {
    this(success, code, message, data, ApiResources.empty(), requestId, timestamp);
  }

  public static <T> ApiResponse<T> ok(
      T data, ApiResources resources, String requestId, Instant timestamp) {
    return new ApiResponse<>(true, SUCCESS_CODE, OK_MESSAGE, data, resources, requestId, timestamp);
  }

  public static <T> ApiResponse<T> success(T data, String requestId) {
    return new ApiResponse<>(
        true, SUCCESS_CODE, SUCCESS_MESSAGE, data, ApiResources.empty(), requestId, Instant.now());
  }

  public static <T> ApiResponse<T> success(T data) {
    return success(data, currentRequestId());
  }

  public static ApiResponse<Void> empty() {
    return success(null, currentRequestId());
  }

  public ApiResponse<T> withResources(ApiResources resources) {
    return new ApiResponse<>(success, code, message, data, resources, requestId, timestamp);
  }

  private static String currentRequestId() {
    var context = RequestContext.currentOrNull();
    if (context != null) {
      return context.requestId();
    }
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      Object requestContext =
          attributes.getAttribute(RequestContext.ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
      if (requestContext instanceof RequestContextView contextView) {
        return contextView.requestId();
      }
    }
    return UUID.randomUUID().toString();
  }
}
