package com.aegisid.foundation.security.outbound;

import java.util.List;
import java.util.Map;

public record SafeOutboundResponse(int statusCode, Map<String, List<String>> headers, String body) {

  public SafeOutboundResponse {
    headers = Map.copyOf(headers == null ? Map.of() : headers);
    body = body == null ? "" : body;
  }
}
