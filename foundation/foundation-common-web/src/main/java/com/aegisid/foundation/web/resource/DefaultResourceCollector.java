package com.aegisid.foundation.web.resource;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DefaultResourceCollector implements ResourceCollector {

  private final Map<ResourceScanner.ReferenceKey, Set<ResourceScanner.Reference>> references =
      new LinkedHashMap<>();
  private final Map<String, List<ResourceOptionItem>> options = new LinkedHashMap<>();

  @Override
  public ResourceCollector ref(Class<? extends ResourceLoader<?>> loaderClass, Object id) {
    return ref(loaderClass, "", "", id);
  }

  @Override
  public ResourceCollector ref(
      Class<? extends ResourceLoader<?>> loaderClass, String domain, String projection, Object id) {
    if (id == null) {
      return this;
    }
    ResourceScanner.ReferenceKey key =
        new ResourceScanner.ReferenceKey(loaderClass, domain, projection);
    references
        .computeIfAbsent(key, ignored -> new LinkedHashSet<>())
        .add(
            new ResourceScanner.Reference(
                String.valueOf(id), FallbackStrategy.DEFAULT, OnNull.SKIP));
    return this;
  }

  @Override
  public ResourceCollector refs(Class<? extends ResourceLoader<?>> loaderClass, Collection<?> ids) {
    return refs(loaderClass, "", "", ids);
  }

  @Override
  public ResourceCollector refs(
      Class<? extends ResourceLoader<?>> loaderClass,
      String domain,
      String projection,
      Collection<?> ids) {
    if (ids == null) {
      return this;
    }
    ids.forEach(id -> ref(loaderClass, domain, projection, id));
    return this;
  }

  @Override
  public ResourceCollector options(String domain, List<ResourceOptionItem> items) {
    options.put(domain == null ? "" : domain, items == null ? List.of() : List.copyOf(items));
    return this;
  }

  @Override
  public ResourceScanner.Result result() {
    Map<ResourceScanner.ReferenceKey, ResourceScanner.ReferenceGroup> groups =
        new LinkedHashMap<>();
    references.forEach(
        (key, groupReferences) -> {
          Set<String> keys = new LinkedHashSet<>();
          for (ResourceScanner.Reference reference : groupReferences) {
            if (reference.key() != null) {
              keys.add(reference.key());
            }
          }
          groups.put(key, new ResourceScanner.ReferenceGroup(keys, groupReferences));
        });
    return new ResourceScanner.Result(groups, Set.of());
  }

  @Override
  public Map<String, List<ResourceOptionItem>> options() {
    Map<String, List<ResourceOptionItem>> copy = new LinkedHashMap<>();
    options.forEach((domain, items) -> copy.put(domain, Collections.unmodifiableList(items)));
    return Collections.unmodifiableMap(copy);
  }
}
