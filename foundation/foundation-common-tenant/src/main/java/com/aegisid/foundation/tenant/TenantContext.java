package com.aegisid.foundation.tenant;

/** Current tenant identity carried by request or job execution context. */
public record TenantContext(String tenantId) {

  public TenantContext {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
  }
}
