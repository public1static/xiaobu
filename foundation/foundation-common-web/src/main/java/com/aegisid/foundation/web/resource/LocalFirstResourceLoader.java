package com.aegisid.foundation.web.resource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class LocalFirstResourceLoader<T> implements ResourceLoader<T> {

  @Override
  public Map<String, T> load(
      String domain, String projection, Set<String> keys, ResourceContext context) {
    Objects.requireNonNull(keys, "keys must not be null");
    Objects.requireNonNull(context, "context must not be null");
    if (keys.isEmpty()) {
      return Map.of();
    }

    Map<String, T> local = emptyIfNull(tryLocal(domain, projection, keys, context));
    Map<String, T> loaded = new LinkedHashMap<>(local);
    Set<String> remaining = new LinkedHashSet<>(keys);
    remaining.removeAll(local.keySet());
    if (!remaining.isEmpty()) {
      loaded.putAll(emptyIfNull(tryRemote(domain, projection, remaining, context)));
    }
    return loaded;
  }

  protected abstract Map<String, T> tryLocal(
      String domain, String projection, Set<String> keys, ResourceContext context);

  protected abstract Map<String, T> tryRemote(
      String domain, String projection, Set<String> keys, ResourceContext context);

  private Map<String, T> emptyIfNull(Map<String, T> resources) {
    return resources == null ? Map.of() : resources;
  }
}
