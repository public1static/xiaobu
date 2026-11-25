package com.aegisid.foundation.security.outbound;

public interface SafeOutboundHttpClient {

  SafeOutboundResponse execute(SafeOutboundRequest request);
}
