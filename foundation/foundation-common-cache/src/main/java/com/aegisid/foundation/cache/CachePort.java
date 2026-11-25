package com.aegisid.foundation.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public interface CachePort {

  <T> Optional<T> get(String key, Class<T> type);

  void put(String key, Object value, Duration ttl);

  <T> T update(String key, Class<T> type, Duration ttl, Function<Optional<T>, T> updater);

  void evict(String key);

  void clear();

  void cleanUp();

  long estimatedSize();

  static String secretKey(String namespace, String rawSecret) {
    Objects.requireNonNull(namespace, "namespace must not be null");
    Objects.requireNonNull(rawSecret, "rawSecret must not be null");
    if (namespace.isBlank()) {
      throw new IllegalArgumentException("namespace must not be blank");
    }
    if (rawSecret.isBlank()) {
      throw new IllegalArgumentException("rawSecret must not be blank");
    }
    return namespace + ":" + sha256(rawSecret);
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
