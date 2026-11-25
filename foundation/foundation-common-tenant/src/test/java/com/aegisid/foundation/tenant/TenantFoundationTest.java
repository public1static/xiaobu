package com.aegisid.foundation.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantFoundationTest {

  @AfterEach
  void clearTenantContext() {
    TenantContextHolder.clear();
  }

  @Test
  void tenantContextHolderCanSaveAndClearCurrentTenant() {
    TenantContext tenantContext = new TenantContext("tenant_acme");

    TenantContextHolder.set(tenantContext);

    assertEquals(tenantContext, TenantContextHolder.currentTenant().orElseThrow());

    TenantContextHolder.clear();

    assertTrue(TenantContextHolder.currentTenant().isEmpty());
  }

  @Test
  void tenantScopedResourceExposesTenantId() {
    TenantScopedResource resource = () -> "tenant_acme";

    assertEquals("tenant_acme", resource.tenantId());
  }
}
