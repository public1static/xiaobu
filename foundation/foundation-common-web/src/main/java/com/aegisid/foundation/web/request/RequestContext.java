package com.aegisid.foundation.web.request;

import com.aegisid.foundation.shared.request.RequestContextView;

/**
 * Request-scoped correlation identifiers exposed to web-layer collaborators.
 *
 * @param requestId request correlation id
 * @param traceId distributed trace id
 */
public record RequestContext(String requestId, String traceId) implements RequestContextView {

  public static final String ATTRIBUTE_NAME = RequestContext.class.getName();

  private static final ThreadLocal<RequestContextView> CURRENT = new ThreadLocal<>();

  public RequestContext {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId must not be blank");
    }
    if (traceId == null || traceId.isBlank()) {
      traceId = requestId;
    }
  }

  public static void setCurrent(RequestContextView context) {
    if (context == null) {
      throw new NullPointerException("context must not be null");
    }
    CURRENT.set(context);
  }

  public static RequestContextView current() {
    RequestContextView context = currentOrNull();
    if (context == null) {
      throw new IllegalStateException("No request context is bound to the current thread");
    }
    return context;
  }

  public static RequestContextView currentOrNull() {
    return CURRENT.get();
  }

  public static void clear() {
    CURRENT.remove();
  }
}
