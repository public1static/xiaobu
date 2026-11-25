package com.aegisid.foundation.shared.request;

/** Read-only view of request correlation identifiers. */
public interface RequestContextView {

  String requestId();

  String traceId();
}
