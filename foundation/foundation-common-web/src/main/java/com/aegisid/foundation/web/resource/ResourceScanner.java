package com.aegisid.foundation.web.resource;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ResourceScanner {

  public Result scan(Object value) {
    ScanState state = new ScanState();
    scanValue(value, state);
    return state.toResult();
  }

  private void scanValue(Object value, ScanState state) {
    if (value == null || state.visited.containsKey(value)) {
      return;
    }
    state.visited.put(value, Boolean.TRUE);

    if (value instanceof Collection<?> collection) {
      collection.forEach(item -> scanValue(item, state));
      return;
    }
    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        scanValue(Array.get(value, i), state);
      }
      return;
    }
    if (value instanceof Map<?, ?> map) {
      map.values().forEach(item -> scanValue(item, state));
      return;
    }
    if (isLeafType(value.getClass())) {
      return;
    }

    for (Field field : fieldsOf(value.getClass())) {
      if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
        continue;
      }
      scanField(value, field, state);
    }
  }

  private void scanField(Object owner, Field field, ScanState state) {
    OptionsRef optionsRef = field.getAnnotation(OptionsRef.class);
    if (optionsRef != null) {
      state.optionDomains.add(new OptionDomain(optionsRef.value(), optionsRef.domain()));
    }

    ResourceRef[] resourceRefs = field.getAnnotationsByType(ResourceRef.class);
    ResourceRefMapKey mapKeyRef = field.getAnnotation(ResourceRefMapKey.class);
    boolean needsValue =
        resourceRefs.length > 0
            || mapKeyRef != null
            || (optionsRef == null
                && (isContainerType(field.getType()) || !isLeafType(field.getType())));
    if (!needsValue) {
      return;
    }

    Object fieldValue = fieldValue(owner, field);
    for (ResourceRef resourceRef : resourceRefs) {
      ReferenceKey key =
          new ReferenceKey(resourceRef.value(), resourceRef.domain(), resourceRef.projection());
      addReferenceValues(state, key, fieldValue, resourceRef.fallback(), resourceRef.onNull());
    }
    if (mapKeyRef != null && fieldValue instanceof Map<?, ?> map) {
      ReferenceKey key =
          new ReferenceKey(mapKeyRef.loader(), mapKeyRef.domain(), mapKeyRef.projection());
      addReferenceValues(state, key, map.keySet(), mapKeyRef.fallback(), OnNull.SKIP);
    }
    scanValue(fieldValue, state);
  }

  private void addReferenceValues(
      ScanState state, ReferenceKey key, Object value, FallbackStrategy fallback, OnNull onNull) {
    if (value == null) {
      if (onNull == OnNull.PLACEHOLDER) {
        addSingleReferenceValue(state, key, null, fallback, onNull);
      }
      return;
    }
    if (value instanceof Collection<?> collection) {
      collection.forEach(item -> addSingleReferenceValue(state, key, item, fallback, onNull));
      return;
    }
    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        addSingleReferenceValue(state, key, Array.get(value, i), fallback, onNull);
      }
      return;
    }
    addSingleReferenceValue(state, key, value, fallback, onNull);
  }

  private void addSingleReferenceValue(
      ScanState state, ReferenceKey key, Object value, FallbackStrategy fallback, OnNull onNull) {
    if (value == null) {
      if (onNull == OnNull.PLACEHOLDER) {
        state
            .references
            .computeIfAbsent(key, ignored -> new LinkedHashSet<>())
            .add(new Reference(null, fallback, onNull));
      }
      return;
    }
    state
        .references
        .computeIfAbsent(key, ignored -> new LinkedHashSet<>())
        .add(new Reference(String.valueOf(value), fallback, onNull));
  }

  private Object fieldValue(Object owner, Field field) {
    try {
      if (!field.trySetAccessible()) {
        return null;
      }
      return field.get(owner);
    } catch (IllegalAccessException | RuntimeException ex) {
      return null;
    }
  }

  private List<Field> fieldsOf(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    Class<?> current = type;
    while (current != null && current != Object.class) {
      Collections.addAll(fields, current.getDeclaredFields());
      current = current.getSuperclass();
    }
    return fields;
  }

  private boolean isLeafType(Class<?> type) {
    return type.isPrimitive()
        || CharSequence.class.isAssignableFrom(type)
        || Number.class.isAssignableFrom(type)
        || Boolean.class == type
        || Character.class == type
        || Enum.class.isAssignableFrom(type)
        || Class.class == type
        || isJdkType(type);
  }

  private boolean isContainerType(Class<?> type) {
    return type.isArray()
        || Collection.class.isAssignableFrom(type)
        || Map.class.isAssignableFrom(type);
  }

  private boolean isJdkType(Class<?> type) {
    Package typePackage = type.getPackage();
    if (typePackage == null) {
      return false;
    }
    String packageName = typePackage.getName();
    return packageName.startsWith("java.")
        || packageName.startsWith("javax.")
        || packageName.startsWith("jakarta.");
  }

  public record Result(
      Map<ReferenceKey, ReferenceGroup> references, Set<OptionDomain> optionDomains) {

    public Result {
      references = copyReferences(references);
      optionDomains = Set.copyOf(optionDomains);
    }
  }

  public record ReferenceKey(
      Class<? extends ResourceLoader<?>> loaderClass, String domain, String projection) {

    public ReferenceKey {
      Objects.requireNonNull(loaderClass, "loaderClass must not be null");
      domain = domain == null ? "" : domain;
      projection = projection == null ? "" : projection;
    }
  }

  public record Reference(String key, FallbackStrategy fallback, OnNull onNull) {

    public Reference {
      fallback = fallback == null ? FallbackStrategy.DEFAULT : fallback;
      onNull = onNull == null ? OnNull.SKIP : onNull;
    }
  }

  public record ReferenceGroup(Set<String> keys, Set<Reference> references) {

    public ReferenceGroup {
      keys = Collections.unmodifiableSet(new LinkedHashSet<>(keys));
      references = Collections.unmodifiableSet(new LinkedHashSet<>(references));
    }

    private static ReferenceGroup from(Set<Reference> references) {
      Set<String> keys = new LinkedHashSet<>();
      for (Reference reference : references) {
        if (reference.key() != null) {
          keys.add(reference.key());
        }
      }
      return new ReferenceGroup(keys, references);
    }
  }

  public record OptionDomain(Class<? extends OptionsLoader> loaderClass, String domain) {

    public OptionDomain {
      Objects.requireNonNull(loaderClass, "loaderClass must not be null");
      domain = domain == null ? "" : domain;
    }
  }

  private static Map<ReferenceKey, ReferenceGroup> copyReferences(
      Map<ReferenceKey, ReferenceGroup> references) {
    Map<ReferenceKey, ReferenceGroup> copy = new LinkedHashMap<>();
    references.forEach(
        (key, group) -> copy.put(key, new ReferenceGroup(group.keys(), group.references())));
    return Collections.unmodifiableMap(copy);
  }

  private static final class ScanState {

    private final Map<Object, Boolean> visited = new IdentityHashMap<>();
    private final Map<ReferenceKey, Set<Reference>> references = new LinkedHashMap<>();
    private final Set<OptionDomain> optionDomains = new LinkedHashSet<>();

    private Result toResult() {
      Map<ReferenceKey, ReferenceGroup> groups = new LinkedHashMap<>();
      references.forEach((key, values) -> groups.put(key, ReferenceGroup.from(values)));
      return new Result(groups, optionDomains);
    }
  }
}
