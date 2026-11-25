package com.aegisid.foundation.security.outbound;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpMethod;

public record SafeOutboundRequest(
    HttpMethod method,
    URI uri,
    Map<String, String> headers,
    String body,
    SafeOutboundPolicy policy) {

  public SafeOutboundRequest {
    if (method == null) {
      throw new IllegalArgumentException("method must not be null");
    }
    if (uri == null) {
      throw new IllegalArgumentException("uri must not be null");
    }
    headers = Map.copyOf(headers == null ? Map.of() : headers);
    if (policy == null) {
      policy = SafeOutboundPolicy.json();
    }
  }

  public static SafeOutboundRequest get(URI uri, SafeOutboundPolicy policy) {
    return new SafeOutboundRequest(HttpMethod.GET, uri, Map.of(), null, policy);
  }

  public static SafeOutboundRequest post(
      URI uri, Map<String, String> headers, String body, SafeOutboundPolicy policy) {
    return new SafeOutboundRequest(HttpMethod.POST, uri, headers, body, policy);
  }
}
