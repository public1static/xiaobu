package com.aegisid.foundation.security.web;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.web.cors.CorsConfiguration;

public record SecurityHeadersConfig(
    Hsts hsts,
    FrameOptions frameOptions,
    boolean contentTypeOptions,
    String contentSecurityPolicy,
    String referrerPolicy,
    String permissionsPolicy,
    CorsConfiguration corsConfiguration) {

  private static final String DEFAULT_CSP =
      "default-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'";
  private static final String DEFAULT_REFERRER_POLICY = "no-referrer";
  private static final String DEFAULT_PERMISSIONS_POLICY =
      "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

  public SecurityHeadersConfig {
    Objects.requireNonNull(hsts, "hsts must not be null");
    Objects.requireNonNull(frameOptions, "frameOptions must not be null");
    Objects.requireNonNull(contentSecurityPolicy, "contentSecurityPolicy must not be null");
    Objects.requireNonNull(referrerPolicy, "referrerPolicy must not be null");
    Objects.requireNonNull(permissionsPolicy, "permissionsPolicy must not be null");
    Objects.requireNonNull(corsConfiguration, "corsConfiguration must not be null");
  }

  public static SecurityHeadersConfig defaults() {
    return new SecurityHeadersConfig(
        new Hsts(Duration.ofDays(365), true, false),
        FrameOptions.DENY,
        true,
        DEFAULT_CSP,
        DEFAULT_REFERRER_POLICY,
        DEFAULT_PERMISSIONS_POLICY,
        baselineCorsConfiguration());
  }

  public static CorsConfiguration baselineCorsConfiguration() {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOriginPatterns(List.of("*"));
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "X-Requested-With", "X-Request-Id"));
    cors.setExposedHeaders(List.of("X-Request-Id"));
    cors.setAllowCredentials(false);
    cors.setMaxAge(Duration.ofHours(1));
    return cors;
  }

  public record Hsts(Duration maxAge, boolean includeSubDomains, boolean preload) {

    public Hsts {
      Objects.requireNonNull(maxAge, "maxAge must not be null");
      if (maxAge.isZero() || maxAge.isNegative()) {
        throw new IllegalArgumentException("maxAge must be positive");
      }
    }
  }

  public enum FrameOptions {
    DENY,
    SAMEORIGIN
  }
}
