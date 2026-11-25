package com.aegisid.foundation.test;

import com.aegisid.foundation.tenant.TenantContext;
import com.aegisid.foundation.tenant.TenantScopedResource;

/** Stable tenant fixtures for foundation tests. */
public final class TenantFixtures {

  public static final String TENANT_ID = "tenant_test";

  private TenantFixtures() {}

  public static String tenantId() {
    return TENANT_ID;
  }

  public static TenantContext tenantContext() {
    return new TenantContext(TENANT_ID);
  }

  public static TestTenantScopedResource tenantScopedResource(String resourceId) {
    return new TestTenantScopedResource(TENANT_ID, resourceId);
  }

  public record TestTenantScopedResource(String tenantId, String resourceId)
      implements TenantScopedResource {}
}
