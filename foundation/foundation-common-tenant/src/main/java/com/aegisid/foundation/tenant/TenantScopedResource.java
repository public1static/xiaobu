package com.aegisid.foundation.tenant;

/** Marker contract for resources owned by a tenant. */
public interface TenantScopedResource {

  String tenantId();
}
