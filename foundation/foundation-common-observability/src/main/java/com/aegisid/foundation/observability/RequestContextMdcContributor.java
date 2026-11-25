package com.aegisid.foundation.observability;

import com.aegisid.foundation.shared.request.RequestContextView;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Owns writes to the platform MDC keys for a request lifecycle. */
@Component
public class RequestContextMdcContributor {

  private final RequestContextMdcBridge bridge;

  public RequestContextMdcContributor() {
    this(new RequestContextMdcBridge());
  }

  public RequestContextMdcContributor(RequestContextMdcBridge bridge) {
    this.bridge = bridge;
  }

  public void contribute(RequestContextView context) {
    MDC.remove(MdcKeys.REQUEST_ID);
    MDC.remove(MdcKeys.TRACE_ID);
    for (Map.Entry<String, String> entry : bridge.toMdcValues(context).entrySet()) {
      MDC.put(entry.getKey(), entry.getValue());
    }
  }

  public void contributeActor(Object actorContext) {
    MDC.remove(MdcKeys.ACTOR_ID);
    MDC.remove(MdcKeys.TENANT_ID);
    for (Map.Entry<String, String> entry : bridge.toActorMdcValues(actorContext).entrySet()) {
      MDC.put(entry.getKey(), entry.getValue());
    }
  }

  public void clear() {
    MDC.remove(MdcKeys.REQUEST_ID);
    MDC.remove(MdcKeys.TRACE_ID);
    MDC.remove(MdcKeys.ACTOR_ID);
    MDC.remove(MdcKeys.TENANT_ID);
  }
}
