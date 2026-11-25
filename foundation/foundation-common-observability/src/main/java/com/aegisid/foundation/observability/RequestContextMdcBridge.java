package com.aegisid.foundation.observability;

import com.aegisid.foundation.shared.request.RequestContextView;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/** Translates the shared request context contract into MDC values. */
public class RequestContextMdcBridge {

  public Map<String, String> toMdcValues(RequestContextView context) {
    if (context == null) {
      return Map.of();
    }

    Map<String, String> values = new LinkedHashMap<>();
    putIfPresent(values, MdcKeys.REQUEST_ID, context.requestId());
    putIfPresent(values, MdcKeys.TRACE_ID, context.traceId());
    return Map.copyOf(values);
  }

  public Map<String, String> toActorMdcValues(Object actorContext) {
    if (actorContext == null) {
      return Map.of();
    }

    Map<String, String> values = new LinkedHashMap<>();
    putIfPresent(values, MdcKeys.ACTOR_ID, valueFrom(actorContext, "actorId"));
    putIfPresent(values, MdcKeys.TENANT_ID, valueFrom(actorContext, "tenantId"));
    return Map.copyOf(values);
  }

  private static void putIfPresent(Map<String, String> values, String key, String value) {
    if (value != null && !value.isBlank()) {
      values.put(key, value);
    }
  }

  private static String valueFrom(Object source, String accessorName) {
    try {
      Method accessor = source.getClass().getDeclaredMethod(accessorName);
      accessor.setAccessible(true);
      Object value = accessor.invoke(source);
      return value instanceof String text ? text : null;
    } catch (ReflectiveOperationException exception) {
      return null;
    }
  }
}
