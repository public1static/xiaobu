package com.aegisid.foundation.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class HttpMethodPolicyFilter extends OncePerRequestFilter {
  private static final Set<String> ALLOWED_METHODS =
      Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
  private static final String ALLOW_HEADER_VALUE = "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS";
  private static final String METHOD_NOT_ALLOWED_BODY = "{\"error\":\"method_not_allowed\"}";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
    if (!ALLOWED_METHODS.contains(method)) {
      reject(response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static void reject(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    response.setHeader(HttpHeaders.ALLOW, ALLOW_HEADER_VALUE);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(METHOD_NOT_ALLOWED_BODY);
  }
}
