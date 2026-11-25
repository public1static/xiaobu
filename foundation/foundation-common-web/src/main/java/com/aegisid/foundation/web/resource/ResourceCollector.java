package com.aegisid.foundation.web.resource;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Request-scoped collector for references that are only known at runtime. */
public interface ResourceCollector {

  ResourceCollector ref(Class<? extends ResourceLoader<?>> loaderClass, Object id);

  ResourceCollector ref(
      Class<? extends ResourceLoader<?>> loaderClass, String domain, String projection, Object id);

  ResourceCollector refs(Class<? extends ResourceLoader<?>> loaderClass, Collection<?> ids);

  ResourceCollector refs(
      Class<? extends ResourceLoader<?>> loaderClass,
      String domain,
      String projection,
      Collection<?> ids);

  ResourceCollector options(String domain, List<ResourceOptionItem> items);

  ResourceScanner.Result result();

  Map<String, List<ResourceOptionItem>> options();
}
