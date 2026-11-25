package com.aegisid.foundation.web.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Establishes request correlation ids for the web request lifecycle. */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-Id";

  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  public static final int MAX_CORRELATION_ID_LENGTH = 128;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = validHeaderOrGenerated(request, REQUEST_ID_HEADER);
    String traceId = validHeaderOrDefault(request, TRACE_ID_HEADER, requestId);
    RequestContext context = new RequestContext(requestId, traceId);

    request.setAttribute(RequestContext.ATTRIBUTE_NAME, context);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    response.setHeader(TRACE_ID_HEADER, traceId);
    RequestContext.setCurrent(context);
    try {
      filterChain.doFilter(request, response);
    } finally {
      RequestContext.clear();
    }
  }

  private static String validHeaderOrGenerated(HttpServletRequest request, String headerName) {
    String value = request.getHeader(headerName);
    if (isValidCorrelationId(value)) {
      return value;
    }
    return UUID.randomUUID().toString();
  }

  private static String validHeaderOrDefault(
      HttpServletRequest request, String headerName, String defaultValue) {
    String value = request.getHeader(headerName);
    if (value == null) {
      return defaultValue;
    }
    if (isValidCorrelationId(value)) {
      return value;
    }
    return UUID.randomUUID().toString();
  }

  private static boolean isValidCorrelationId(String value) {
    if (value == null || value.isBlank() || value.length() > MAX_CORRELATION_ID_LENGTH) {
      return false;
    }
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (!isTokenCharacter(current)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isTokenCharacter(char value) {
    return (value >= 'A' && value <= 'Z')
        || (value >= 'a' && value <= 'z')
        || (value >= '0' && value <= '9')
        || value == '-'
        || value == '_'
        || value == '.'
        || value == ':';
  }
}
