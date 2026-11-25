package com.aegisid.foundation.audit;

import com.aegisid.foundation.shared.text.Texts;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable audit event envelope. Business-specific fields belong in details. */
public record AuditEvent(
    UUID eventId,
    Instant occurredAt,
    Optional<String> tenantId,
    String actorId,
    String action,
    String resourceType,
    String resourceId,
    Map<String, String> details) {

  public AuditEvent {
    eventId = Objects.requireNonNull(eventId, "eventId must not be null");
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    tenantId.ifPresent(
        value -> {
          if (value.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
          }
        });
    actorId = Texts.require(actorId, "actorId");
    action = Texts.require(action, "action");
    resourceType = Texts.require(resourceType, "resourceType");
    resourceId = Texts.require(resourceId, "resourceId");
    details = Map.copyOf(Objects.requireNonNull(details, "details must not be null"));
  }
}
