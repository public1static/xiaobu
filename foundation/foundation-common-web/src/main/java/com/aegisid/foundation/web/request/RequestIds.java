package com.aegisid.foundation.web.request;

import com.aegisid.foundation.shared.request.RequestContextView;
import java.util.UUID;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public final class RequestIds {

  private RequestIds() {}

  public static String currentOrGenerate() {
    RequestContextView current = RequestContext.currentOrNull();
    if (current != null) {
      return current.requestId();
    }

    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      Object context =
          attributes.getAttribute(RequestContext.ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
      if (context instanceof RequestContextView requestContext) {
        return requestContext.requestId();
      }
    }
    return UUID.randomUUID().toString();
  }
}
