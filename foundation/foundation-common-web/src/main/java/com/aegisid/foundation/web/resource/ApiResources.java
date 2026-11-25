package com.aegisid.foundation.web.resource;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Top-level display resources container. */
public final class ApiResources {

  private final Map<String, Object> values;
  private final boolean mutable;

  private ApiResources(boolean mutable) {
    this.values = new LinkedHashMap<>();
    this.mutable = mutable;
  }

  private ApiResources(Map<String, Object> values, boolean mutable) {
    this.values = values;
    this.mutable = mutable;
  }

  public static ApiResources empty() {
    return new ApiResources(true);
  }

  public static ApiResources copyOf(ApiResources resources) {
    if (resources == null || resources.isEmpty()) {
      return new ApiResources(Collections.emptyMap(), false);
    }
    return new ApiResources(deepCopyMap(resources.values), false);
  }

  public Object put(String key, Object value) {
    ensureMutable();
    return values.put(key, value);
  }

  public void putAll(Map<? extends String, ?> resources) {
    ensureMutable();
    resources.forEach(values::put);
  }

  public void putLoadedResource(String resourceType, String domain, String key, Object value) {
    ensureMutable();
    if ("enums".equals(resourceType) || "dicts".equals(resourceType)) {
      nestedMap(nestedMap(resourceType), domain).put(key, value);
      return;
    }
    nestedMap(resourceType).put(key, value);
  }

  public void putOptions(String domain, List<ResourceOptionItem> items) {
    ensureMutable();
    nestedMap("options").put(domain, items == null ? List.of() : List.copyOf(items));
  }

  @JsonIgnore
  public boolean isEmpty() {
    return values.isEmpty();
  }

  @JsonIgnore
  public Collection<Object> values() {
    return values.values();
  }

  @JsonIgnore
  public Set<Map.Entry<String, Object>> entrySet() {
    return values.entrySet();
  }

  @JsonAnyGetter
  public Map<String, Object> asMap() {
    return values;
  }

  private void ensureMutable() {
    if (!mutable) {
      throw new UnsupportedOperationException("ApiResources snapshot is immutable");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> nestedMap(String key) {
    return (Map<String, Object>)
        values.computeIfAbsent(key, ignored -> new LinkedHashMap<String, Object>());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> nestedMap(Map<String, Object> owner, String key) {
    return (Map<String, Object>)
        owner.computeIfAbsent(key, ignored -> new LinkedHashMap<String, Object>());
  }

  private static Map<String, Object> deepCopyMap(Map<String, ?> source) {
    Map<String, Object> copied = new LinkedHashMap<>();
    source.forEach((key, value) -> copied.put(key, deepCopyValue(value)));
    return Collections.unmodifiableMap(copied);
  }

  private static Object deepCopyValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copied = new LinkedHashMap<>();
      map.forEach(
          (key, nestedValue) -> copied.put(String.valueOf(key), deepCopyValue(nestedValue)));
      return Collections.unmodifiableMap(copied);
    }
    if (value instanceof List<?> list) {
      return Collections.unmodifiableList(list.stream().map(ApiResources::deepCopyValue).toList());
    }
    if (value instanceof Set<?> set) {
      Set<Object> copied = new LinkedHashSet<>();
      set.forEach(item -> copied.add(deepCopyValue(item)));
      return Collections.unmodifiableSet(copied);
    }
    return value;
  }
}
