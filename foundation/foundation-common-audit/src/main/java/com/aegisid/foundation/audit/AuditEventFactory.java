package com.aegisid.foundation.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Creates audit events from the current audit context. */
public class AuditEventFactory {

  public static final String DEFAULT_ACTOR_ID = "system";

  private final AuditContextResolver contextResolver;
  private final Clock clock;

  public AuditEventFactory(AuditContextResolver contextResolver, Clock clock) {
    this.contextResolver =
        Objects.requireNonNull(contextResolver, "contextResolver must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public AuditEvent create(
      String action, String resourceType, String resourceId, Map<String, String> details) {
    return createAt(
        clock.instant(),
        contextResolver.tenantId(),
        contextResolver.actorId().orElse(DEFAULT_ACTOR_ID),
        action,
        resourceType,
        resourceId,
        details);
  }

  public static AuditEvent createAt(
      Instant occurredAt,
      Optional<String> tenantId,
      String actorId,
      String action,
      String resourceType,
      String resourceId,
      Map<String, String> details) {
    return new AuditEvent(
        UUID.randomUUID(),
        occurredAt,
        tenantId,
        actorId,
        action,
        resourceType,
        resourceId,
        details);
  }
}
