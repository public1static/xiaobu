package com.aegisid.foundation.security.outbound;

import java.time.Duration;
import java.util.List;

public record SafeOutboundPolicy(
    boolean allowRedirects,
    long maxResponseBytes,
    Duration timeout,
    List<String> allowedContentTypePrefixes) {

  private static final long DEFAULT_MAX_RESPONSE_BYTES = 1024 * 1024;
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

  public SafeOutboundPolicy {
    if (maxResponseBytes <= 0) {
      throw new IllegalArgumentException("maxResponseBytes must be positive");
    }
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    allowedContentTypePrefixes =
        List.copyOf(allowedContentTypePrefixes == null ? List.of() : allowedContentTypePrefixes);
  }

  public static SafeOutboundPolicy json() {
    return new SafeOutboundPolicy(
        false,
        DEFAULT_MAX_RESPONSE_BYTES,
        DEFAULT_TIMEOUT,
        List.of("application/json", "application/jwk-set+json"));
  }

  public static SafeOutboundPolicy xml() {
    return new SafeOutboundPolicy(
        false, DEFAULT_MAX_RESPONSE_BYTES, DEFAULT_TIMEOUT, List.of("application/xml", "text/xml"));
  }

  public static SafeOutboundPolicy webhook() {
    return new SafeOutboundPolicy(false, DEFAULT_MAX_RESPONSE_BYTES, DEFAULT_TIMEOUT, List.of());
  }

  public static SafeOutboundPolicy webhookAllowingRedirects() {
    return new SafeOutboundPolicy(true, DEFAULT_MAX_RESPONSE_BYTES, DEFAULT_TIMEOUT, List.of());
  }
}
