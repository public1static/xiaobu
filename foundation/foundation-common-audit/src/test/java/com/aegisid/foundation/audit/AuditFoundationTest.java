package com.aegisid.foundation.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditFoundationTest {

  @Test
  void auditEventFactoryBuildsEventFromClockAndContext() {
    AuditContextResolver contextResolver =
        new AuditContextResolver() {
          @Override
          public Optional<String> tenantId() {
            return Optional.of("tenant_acme");
          }

          @Override
          public Optional<String> actorId() {
            return Optional.of("user_123");
          }
        };
    AuditEventFactory factory =
        new AuditEventFactory(
            contextResolver,
            java.time.Clock.fixed(Instant.parse("2026-04-25T01:02:03Z"), java.time.ZoneOffset.UTC));

    AuditEvent event =
        factory.create(
            "oauth.token.issued",
            "oauth-client",
            "client_123",
            Map.of("grantType", "authorization_code"));

    assertEquals(Instant.parse("2026-04-25T01:02:03Z"), event.occurredAt());
    assertEquals(Optional.of("tenant_acme"), event.tenantId());
    assertEquals("user_123", event.actorId());
    assertEquals("oauth.token.issued", event.action());
    assertEquals("oauth-client", event.resourceType());
    assertEquals("client_123", event.resourceId());
    assertEquals("authorization_code", event.details().get("grantType"));
  }

  @Test
  void auditEventFactoryFallsBackToSystemActor() {
    AuditEventFactory factory =
        new AuditEventFactory(
            new AuditContextResolver() {},
            java.time.Clock.fixed(Instant.parse("2026-04-25T01:02:03Z"), java.time.ZoneOffset.UTC));

    AuditEvent event = factory.create("credential.deleted", "credential", "cred_123", Map.of());

    assertEquals(Optional.empty(), event.tenantId());
    assertEquals("system", event.actorId());
  }

  @Test
  void auditEventFactoryBuildsEventFromExplicitEnvelopeContext() {
    Instant occurredAt = Instant.parse("2026-04-25T01:02:03Z");

    AuditEvent event =
        AuditEventFactory.createAt(
            occurredAt,
            Optional.of("tenant_acme"),
            "user_123",
            "credential.deleted",
            "credential",
            "cred_123",
            Map.of("outcome", "SUCCESS"));

    assertNotNull(event.eventId());
    assertEquals(occurredAt, event.occurredAt());
    assertEquals(Optional.of("tenant_acme"), event.tenantId());
    assertEquals("user_123", event.actorId());
    assertEquals("credential.deleted", event.action());
    assertEquals("credential", event.resourceType());
    assertEquals("cred_123", event.resourceId());
    assertEquals("SUCCESS", event.details().get("outcome"));
  }

  @Test
  void auditEventSupportsTenantIdAsOptionalString() {
    AuditEvent event =
        new AuditEvent(
            UUID.randomUUID(),
            Instant.parse("2026-04-25T01:02:03Z"),
            Optional.of("tenant_acme"),
            "user_123",
            "oauth.token.issued",
            "oauth-client",
            "client_123",
            Map.of());

    assertEquals(Optional.of("tenant_acme"), event.tenantId());
  }

  @Test
  void auditEventCanCarryBusinessSpecificDetails() {
    AuditEvent event =
        new AuditEvent(
            UUID.randomUUID(),
            Instant.parse("2026-04-25T01:02:03Z"),
            Optional.empty(),
            "user_123",
            "oauth.token.issued",
            "oauth-client",
            "client_123",
            Map.of(
                "userPoolId", "pool_123",
                "applicationId", "app_123",
                "clientId", "client_123",
                "grantType", "authorization_code"));

    assertEquals("pool_123", event.details().get("userPoolId"));
    assertEquals("app_123", event.details().get("applicationId"));
    assertEquals("client_123", event.details().get("clientId"));
    assertEquals("authorization_code", event.details().get("grantType"));
  }

  @Test
  void auditWriterFailureCanBeConfiguredToBlockSecurityCriticalOperations() {
    AuditEvent event =
        new AuditEvent(
            UUID.randomUUID(),
            Instant.parse("2026-04-25T01:02:03Z"),
            Optional.empty(),
            "user_123",
            "credential.deleted",
            "credential",
            "cred_123",
            Map.of());
    AuditWriter failingWriter =
        ignored -> {
          throw new IllegalStateException("audit backend unavailable");
        };

    assertDoesNotThrow(() -> failingWriter.write(event, AuditWriter.FailurePolicy.BEST_EFFORT));
    assertThrows(
        IllegalStateException.class,
        () -> failingWriter.write(event, AuditWriter.FailurePolicy.BLOCKING));
  }
}
