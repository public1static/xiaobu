package com.aegisid.foundation.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BusinessAuditTemplateTest {
  private final List<AuditEvent> events = new ArrayList<>();
  private final BusinessAuditTemplate template =
      new BusinessAuditTemplate(
          events::add, Clock.fixed(Instant.parse("2026-05-31T01:02:03Z"), ZoneOffset.UTC));

  @Test
  void writesSuccessAuditAfterActionCompletes() {
    BusinessAuditCommand command = command();

    String result = template.execute(command, () -> "created");

    assertEquals("created", result);
    assertEquals(1, events.size());
    AuditEvent event = events.get(0);
    assertEquals(Instant.parse("2026-05-31T01:02:03Z"), event.occurredAt());
    assertEquals(Optional.of("tenant_acme"), event.tenantId());
    assertEquals("user_console", event.actorId());
    assertEquals("console.user.created", event.action());
    assertEquals("identity.user", event.resourceType());
    assertEquals("user_member", event.resourceId());
    assertEquals("pool_acme", event.details().get("userPoolId"));
    assertEquals("success", event.details().get("result"));
  }

  @Test
  void writesFailureAuditAndRethrows() {
    BusinessAuditCommand command = command();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                template.execute(
                    command,
                    () -> {
                      throw new IllegalStateException("identity backend unavailable");
                    }));

    assertEquals("identity backend unavailable", exception.getMessage());
    assertEquals(1, events.size());
    AuditEvent event = events.get(0);
    assertEquals("failure", event.details().get("result"));
    assertEquals("identity backend unavailable", event.details().get("failure_reason"));
    assertEquals("pool_acme", event.details().get("userPoolId"));
  }

  private static BusinessAuditCommand command() {
    return new BusinessAuditCommand(
        "console.user.created",
        Optional.of("user_console"),
        Optional.of("tenant_acme"),
        "identity.user",
        "user_member",
        Map.of("userPoolId", "pool_acme"),
        AuditWriter.FailurePolicy.BLOCKING);
  }
}
