package com.aegisid.foundation.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.web.filter.OncePerRequestFilter;

public class SecurityHeadersFilter extends OncePerRequestFilter {

  private static final String HEADER_HSTS = "Strict-Transport-Security";
  private static final String HEADER_FRAME_OPTIONS = "X-Frame-Options";
  private static final String HEADER_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
  private static final String HEADER_CSP = "Content-Security-Policy";
  private static final String HEADER_REFERRER_POLICY = "Referrer-Policy";
  private static final String HEADER_PERMISSIONS_POLICY = "Permissions-Policy";

  private final SecurityHeadersConfig config;

  public SecurityHeadersFilter() {
    this(SecurityHeadersConfig.defaults());
  }

  public SecurityHeadersFilter(SecurityHeadersConfig config) {
    this.config = Objects.requireNonNull(config, "config must not be null");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    applyHeaders(request, response);
    filterChain.doFilter(request, response);
  }

  private void applyHeaders(HttpServletRequest request, HttpServletResponse response) {
    setIfAbsent(response, HEADER_CSP, config.contentSecurityPolicy());
    setIfAbsent(response, HEADER_REFERRER_POLICY, config.referrerPolicy());
    setIfAbsent(response, HEADER_PERMISSIONS_POLICY, config.permissionsPolicy());
    if (config.contentTypeOptions()) {
      setIfAbsent(response, HEADER_CONTENT_TYPE_OPTIONS, "nosniff");
    }
    setIfAbsent(response, HEADER_FRAME_OPTIONS, frameOptionsValue());
    if (request.isSecure()) {
      setIfAbsent(response, HEADER_HSTS, hstsValue());
    }
  }

  private String frameOptionsValue() {
    return switch (config.frameOptions()) {
      case DENY -> "DENY";
      case SAMEORIGIN -> "SAMEORIGIN";
    };
  }

  private String hstsValue() {
    long seconds = config.hsts().maxAge().toSeconds();
    StringBuilder value = new StringBuilder("max-age=").append(seconds);
    if (config.hsts().includeSubDomains()) {
      value.append(" ; includeSubDomains");
    }
    if (config.hsts().preload()) {
      value.append(" ; preload");
    }
    return value.toString();
  }

  private static void setIfAbsent(HttpServletResponse response, String name, String value) {
    if (!response.containsHeader(name)) {
      response.setHeader(name, value);
    }
  }
}
