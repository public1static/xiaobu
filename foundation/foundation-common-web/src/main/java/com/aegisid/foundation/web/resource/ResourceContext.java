package com.aegisid.foundation.web.resource;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public record ResourceContext(
    String tenantId,
    String currentUserId,
    Locale locale,
    Set<String> authorities,
    String requestId) {

  public ResourceContext {
    authorities =
        authorities == null
            ? Set.of()
            : Collections.unmodifiableSet(new LinkedHashSet<>(authorities));
  }
}
