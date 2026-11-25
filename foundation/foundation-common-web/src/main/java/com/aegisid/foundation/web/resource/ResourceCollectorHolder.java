package com.aegisid.foundation.web.resource;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public final class ResourceCollectorHolder {

  static final String ATTRIBUTE_NAME = ResourceCollectorHolder.class.getName() + ".COLLECTOR";

  private final ThreadLocal<ResourceCollector> fallbackCollector = new ThreadLocal<>();

  public ResourceCollector current() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      ResourceCollector collector = fallbackCollector.get();
      if (collector == null) {
        collector = new DefaultResourceCollector();
        fallbackCollector.set(collector);
      }
      return collector;
    }

    Object existing = attributes.getAttribute(ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
    if (existing instanceof ResourceCollector collector) {
      return collector;
    }
    ResourceCollector collector = new DefaultResourceCollector();
    attributes.setAttribute(ATTRIBUTE_NAME, collector, RequestAttributes.SCOPE_REQUEST);
    return collector;
  }

  ResourceCollector currentOrEmpty() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      ResourceCollector collector = fallbackCollector.get();
      return collector == null ? new DefaultResourceCollector() : collector;
    }
    Object existing = attributes.getAttribute(ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
    return existing instanceof ResourceCollector collector
        ? collector
        : new DefaultResourceCollector();
  }

  void clear() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      attributes.removeAttribute(ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
    }
    fallbackCollector.remove();
  }
}
