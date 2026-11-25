package com.aegisid.foundation.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aegisid.foundation.audit.AuditEvent;

/** Assertions for required audit event fields. */
public final class AuditAssertions {

  private AuditAssertions() {}

  public static void assertRequiredFields(AuditEvent event) {
    assertNotNull(event, "event must not be null");
    assertNotNull(event.eventId(), "eventId must be present");
    assertNotNull(event.occurredAt(), "occurredAt must be present");
    assertFalse(event.actorId().isBlank(), "actorId must be present");
    assertFalse(event.action().isBlank(), "action must be present");
    assertFalse(event.resourceType().isBlank(), "resourceType must be present");
    assertFalse(event.resourceId().isBlank(), "resourceId must be present");
    assertNotNull(event.details(), "details must not be null");
  }

  public static void assertAction(AuditEvent event, String action) {
    assertRequiredFields(event);
    assertEquals(action, event.action());
  }

  public static void assertTenant(AuditEvent event, String tenantId) {
    assertRequiredFields(event);
    assertEquals(tenantId, event.tenantId().orElseThrow());
  }
}
