package com.aegisid.foundation.observability;

/** Stable MDC keys used by platform observability components. */
public final class MdcKeys {

  public static final String REQUEST_ID = "requestId";

  public static final String TRACE_ID = "traceId";

  public static final String ACTOR_ID = "actorId";

  public static final String TENANT_ID = "tenantId";

  private MdcKeys() {}
}
