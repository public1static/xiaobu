package com.aegisid.foundation.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aegisid.foundation.audit.AuditEvent;
import com.aegisid.foundation.web.api.ApiErrorResponse;
import com.aegisid.foundation.web.api.ApiResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FoundationTestSupportTest {

  @Test
  void apiAssertionsValidateSuccessAndErrorEnvelopes() {
    ApiAssertions.assertSuccess(ApiResponse.success(Map.of("id", "app_001"), "req_123"), "req_123");

    ApiAssertions.assertError(
        ApiErrorResponse.failure(
            "VALIDATION_FAILED", "参数校验失败", "req_123", Map.of("field", "email")),
        "VALIDATION_FAILED",
        "req_123");
  }

  @Test
  void auditAssertionsValidateRequiredAuditFields() {
    AuditEvent event =
        new AuditEvent(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            Instant.parse("2026-04-25T10:00:00Z"),
            Optional.of("tenant_001"),
            "user_001",
            "APPLICATION_CREATED",
            "application",
            "app_001",
            Map.of("source", "test"));

    AuditAssertions.assertRequiredFields(event);
    AuditAssertions.assertAction(event, "APPLICATION_CREATED");
    AuditAssertions.assertTenant(event, "tenant_001");
  }

  @Test
  void tenantFixturesCreateStableTenantIds() {
    assertEquals("tenant_test", TenantFixtures.tenantId());
    assertEquals("tenant_test", TenantFixtures.tenantContext().tenantId());
    assertEquals("tenant_test", TenantFixtures.tenantScopedResource("app_001").tenantId());
    assertEquals("app_001", TenantFixtures.tenantScopedResource("app_001").resourceId());
  }
}
