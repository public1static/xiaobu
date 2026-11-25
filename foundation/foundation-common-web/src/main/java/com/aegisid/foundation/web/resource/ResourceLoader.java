package com.aegisid.foundation.web.resource;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

public interface ResourceLoader<T> {

  String resourceType();

  Map<String, T> load(String domain, String projection, Set<String> keys, ResourceContext context);

  default String defaultDomain() {
    return "";
  }

  default Duration cacheTtl() {
    return Duration.ZERO;
  }

  default Duration timeout() {
    return Duration.ofMillis(300);
  }

  default FallbackStrategy fallback() {
    return switch (String.valueOf(resourceType())) {
      case "enums", "dicts" -> FallbackStrategy.LABEL_AS_KEY;
      default -> FallbackStrategy.MISSING_PLACEHOLDER;
    };
  }

  default T missingPlaceholder(String key, ResourceContext context) {
    return null;
  }

  default T deletedPlaceholder(String key, ResourceContext context) {
    return null;
  }
}
