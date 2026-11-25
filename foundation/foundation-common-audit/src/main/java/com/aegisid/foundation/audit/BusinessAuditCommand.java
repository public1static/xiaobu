package com.aegisid.foundation.audit;

import com.aegisid.foundation.shared.text.Texts;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record BusinessAuditCommand(
    String action,
    Optional<String> actorId,
    Optional<String> tenantId,
    String resourceType,
    String resourceId,
    Map<String, String> details,
    AuditWriter.FailurePolicy failurePolicy) {

  public BusinessAuditCommand {
    action = Texts.require(action, "action");
    actorId = Objects.requireNonNull(actorId, "actorId must not be null");
    actorId.ifPresent(value -> Texts.require(value, "actorId"));
    tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    tenantId.ifPresent(value -> Texts.require(value, "tenantId"));
    resourceType = Texts.require(resourceType, "resourceType");
    resourceId = Texts.require(resourceId, "resourceId");
    details = Map.copyOf(Objects.requireNonNull(details, "details must not be null"));
    failurePolicy = failurePolicy == null ? AuditWriter.FailurePolicy.BLOCKING : failurePolicy;
  }
}
