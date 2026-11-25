package com.aegisid.foundation.audit;

import java.util.Optional;

/** Resolves optional audit context without depending on tenant or security modules. */
public interface AuditContextResolver {

  default Optional<String> tenantId() {
    return Optional.empty();
  }

  default Optional<String> actorId() {
    return Optional.empty();
  }
}
