package com.aegisid.foundation.security.attempt;

import com.aegisid.foundation.cache.CachePort;
import java.time.Duration;
import java.util.Objects;

public class LoginAttemptLimiter {

  private final CachePort cache;
  private final int threshold;
  private final Duration ttl;

  public LoginAttemptLimiter(CachePort cache, int threshold, Duration ttl) {
    this.cache = Objects.requireNonNull(cache, "cache must not be null");
    this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
    if (threshold < 1) {
      throw new IllegalArgumentException("threshold must be at least 1");
    }
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
    this.threshold = threshold;
  }

  public int recordFailure(String subject) {
    String key = cacheKey(subject);
    return cache.update(key, Integer.class, ttl, count -> count.orElse(0) + 1);
  }

  public boolean isLocked(String subject) {
    return failureCount(subject) >= threshold;
  }

  public int failureCount(String subject) {
    return cache.get(cacheKey(subject), Integer.class).orElse(0);
  }

  public void reset(String subject) {
    cache.evict(cacheKey(subject));
  }

  public static String cacheKey(String subject) {
    Objects.requireNonNull(subject, "subject must not be null");
    if (subject.isBlank()) {
      throw new IllegalArgumentException("subject must not be blank");
    }
    return CachePort.secretKey("login-attempt", subject);
  }
}
