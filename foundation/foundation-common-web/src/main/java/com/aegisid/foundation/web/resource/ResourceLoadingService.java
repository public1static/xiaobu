package com.aegisid.foundation.web.resource;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class ResourceLoadingService {

  private static final Duration DEFAULT_LOADER_TIMEOUT = Duration.ofMillis(300);
  private static final int DEFAULT_LOADER_THREADS =
      Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
  private static final int DEFAULT_LOADER_QUEUE_CAPACITY = DEFAULT_LOADER_THREADS * 32;
  private static final AtomicInteger LOADER_THREAD_COUNTER = new AtomicInteger();
  private static final ExecutorService DEFAULT_LOADER_EXECUTOR =
      new ThreadPoolExecutor(
          DEFAULT_LOADER_THREADS,
          DEFAULT_LOADER_THREADS,
          0L,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(DEFAULT_LOADER_QUEUE_CAPACITY),
          loaderThreadFactory(),
          new ThreadPoolExecutor.AbortPolicy());
  private static final String ENUMS = "enums";
  private static final String DICTS = "dicts";
  private static final String OPTIONS = "options";
  private static final String DEFAULT_RESOURCE_TYPE = "resources";
  private static final String NULL_REFERENCE_KEY = "_null";

  private final List<ResourceLoader<?>> resourceLoaders;
  private final List<OptionsLoader> optionsLoaders;
  private final ExecutorService loaderExecutor;

  public ResourceLoadingService(
      Collection<? extends ResourceLoader<?>> resourceLoaders,
      Collection<? extends OptionsLoader> optionsLoaders) {
    this(resourceLoaders, optionsLoaders, DEFAULT_LOADER_EXECUTOR);
  }

  ResourceLoadingService(
      Collection<? extends ResourceLoader<?>> resourceLoaders,
      Collection<? extends OptionsLoader> optionsLoaders,
      ExecutorService loaderExecutor) {
    this.resourceLoaders = resourceLoaders == null ? List.of() : List.copyOf(resourceLoaders);
    this.optionsLoaders = optionsLoaders == null ? List.of() : List.copyOf(optionsLoaders);
    this.loaderExecutor = Objects.requireNonNull(loaderExecutor, "loaderExecutor must not be null");
  }

  public ApiResources load(ResourceScanner.Result scanned, ResourceContext context) {
    return load(scanned, new DefaultResourceCollector(), context);
  }

  public ApiResources load(
      ResourceScanner.Result scanned, ResourceCollector collector, ResourceContext context) {
    Objects.requireNonNull(context, "context must not be null");
    ApiResources resources = ApiResources.empty();
    Map<ResourceScanner.ReferenceKey, LinkedHashSet<ResourceScanner.Reference>> references =
        mergeReferences(scanned, collector);

    references.forEach(
        (key, groupReferences) -> loadResourceGroup(key, groupReferences, context, resources));
    loadOptions(scanned, context, resources);
    if (collector != null) {
      collector.options().forEach(resources::putOptions);
    }
    return ApiResources.copyOf(resources);
  }

  @SuppressWarnings("unchecked")
  private void loadResourceGroup(
      ResourceScanner.ReferenceKey key,
      LinkedHashSet<ResourceScanner.Reference> references,
      ResourceContext context,
      ApiResources resources) {
    ResourceLoader<Object> loader =
        (ResourceLoader<Object>) resolveResourceLoader(key.loaderClass());
    if (loader == null) {
      return;
    }

    String resourceType = safeResourceType(loader);
    String domain = effectiveDomain(key.domain(), safeDefaultDomain(loader));
    String projection = key.projection();
    Set<String> keys = keysOf(references);
    Map<String, Object> loaded = Map.of();
    if (!keys.isEmpty()) {
      try {
        loaded = emptyIfNull(loadWithTimeout(loader, domain, projection, keys, context));
      } catch (RuntimeException ex) {
        loaded = Map.of();
      }
    }

    for (ResourceScanner.Reference reference : references) {
      String resourceKey = resourceKeyOf(reference);
      if (resourceKey == null
          || (reference.key() == null && reference.onNull() != OnNull.PLACEHOLDER)) {
        continue;
      }
      Object resource = loaded.get(resourceKey);
      if (resource == null) {
        resource = fallbackResource(loader, reference, resourceType, resourceKey, context);
      } else if (isDeleted(resource)) {
        resource = deletedPlaceholder(loader, resourceKey, context);
      }
      if (resource != null) {
        resources.putLoadedResource(resourceType, domain, resourceKey, resource);
      }
    }
  }

  private Object fallbackResource(
      ResourceLoader<Object> loader,
      ResourceScanner.Reference reference,
      String resourceType,
      String resourceKey,
      ResourceContext context) {
    FallbackStrategy fallback =
        reference.fallback() == FallbackStrategy.DEFAULT
            ? safeFallback(loader, resourceType)
            : reference.fallback();
    if (fallback == FallbackStrategy.DEFAULT) {
      fallback = defaultFallback(resourceType);
    }
    return switch (fallback) {
      case DEFAULT -> null;
      case MISSING_PLACEHOLDER -> missingPlaceholder(loader, resourceKey, context);
      case LABEL_AS_KEY -> labelAsKey(resourceType, resourceKey);
      case SKIP -> null;
    };
  }

  private Object missingPlaceholder(
      ResourceLoader<Object> loader, String key, ResourceContext context) {
    Object placeholder;
    try {
      placeholder = loader.missingPlaceholder(key, context);
    } catch (RuntimeException ex) {
      placeholder = null;
    }
    return placeholder == null
        ? lifecyclePlaceholder(key, ResourceStatus.MISSING)
        : ensureId(placeholder, key);
  }

  private Object deletedPlaceholder(
      ResourceLoader<Object> loader, String key, ResourceContext context) {
    Object placeholder;
    try {
      placeholder = loader.deletedPlaceholder(key, context);
    } catch (RuntimeException ex) {
      placeholder = null;
    }
    return placeholder == null
        ? lifecyclePlaceholder(key, ResourceStatus.DELETED)
        : ensureId(placeholder, key);
  }

  private Object labelAsKey(String resourceType, String key) {
    return switch (String.valueOf(resourceType)) {
      case ENUMS -> ResourceEnumItem.labelOnly(key);
      case DICTS -> ResourceDictItem.labelOnly(key);
      default -> lifecyclePlaceholder(key, ResourceStatus.MISSING);
    };
  }

  private FallbackStrategy safeFallback(ResourceLoader<Object> loader, String resourceType) {
    try {
      FallbackStrategy fallback = loader.fallback();
      return fallback == null ? defaultFallback(resourceType) : fallback;
    } catch (RuntimeException ex) {
      return defaultFallback(resourceType);
    }
  }

  private FallbackStrategy defaultFallback(String resourceType) {
    return switch (String.valueOf(resourceType)) {
      case ENUMS, DICTS -> FallbackStrategy.LABEL_AS_KEY;
      default -> FallbackStrategy.MISSING_PLACEHOLDER;
    };
  }

  private Duration safeTimeout(ResourceLoader<?> loader) {
    try {
      Duration timeout = loader.timeout();
      return timeout == null ? DEFAULT_LOADER_TIMEOUT : timeout;
    } catch (RuntimeException ex) {
      return DEFAULT_LOADER_TIMEOUT;
    }
  }

  private Map<String, Object> loadWithTimeout(
      ResourceLoader<Object> loader,
      String domain,
      String projection,
      Set<String> keys,
      ResourceContext context) {
    Duration timeout = safeTimeout(loader);
    if (timeout.isZero() || timeout.isNegative()) {
      return loader.load(domain, projection, keys, context);
    }

    Future<Map<String, Object>> future;
    try {
      future = loaderExecutor.submit(() -> loader.load(domain, projection, keys, context));
    } catch (RejectedExecutionException ex) {
      return Map.of();
    }
    try {
      return future.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      return Map.of();
    } catch (ExecutionException | TimeoutException ex) {
      future.cancel(true);
      return Map.of();
    }
  }

  private static ThreadFactory loaderThreadFactory() {
    return runnable -> {
      Thread thread =
          new Thread(
              runnable, "aegisid-resource-loader-" + LOADER_THREAD_COUNTER.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }

  private String safeResourceType(ResourceLoader<?> loader) {
    try {
      String resourceType = loader.resourceType();
      return resourceType == null || resourceType.isBlank() ? DEFAULT_RESOURCE_TYPE : resourceType;
    } catch (RuntimeException ex) {
      return DEFAULT_RESOURCE_TYPE;
    }
  }

  private String safeDefaultDomain(ResourceLoader<?> loader) {
    try {
      String domain = loader.defaultDomain();
      return domain == null ? "" : domain;
    } catch (RuntimeException ex) {
      return "";
    }
  }

  private String safeDefaultDomain(OptionsLoader loader) {
    try {
      String domain = loader.defaultDomain();
      return domain == null ? "" : domain;
    } catch (RuntimeException ex) {
      return "";
    }
  }

  private Map<String, Object> lifecyclePlaceholder(String key, ResourceStatus status) {
    Map<String, Object> placeholder = new LinkedHashMap<>();
    placeholder.put("id", key);
    placeholder.put("displayName", key);
    placeholder.put("status", status);
    return placeholder;
  }

  private Object ensureId(Object resource, String key) {
    if (resource instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((field, value) -> copy.put(String.valueOf(field), value));
      copy.put("id", key);
      return copy;
    }
    return resource;
  }

  private boolean isDeleted(Object resource) {
    Object status = statusOf(resource);
    if (status == ResourceStatus.DELETED) {
      return true;
    }
    return status != null
        && ResourceStatus.DELETED.value().equalsIgnoreCase(String.valueOf(status));
  }

  private Object statusOf(Object resource) {
    if (resource instanceof Map<?, ?> map) {
      return map.get("status");
    }
    try {
      Method method = resource.getClass().getMethod("status");
      return method.invoke(resource);
    } catch (ReflectiveOperationException | RuntimeException ex) {
      return null;
    }
  }

  private void loadOptions(
      ResourceScanner.Result scanned, ResourceContext context, ApiResources resources) {
    if (scanned == null) {
      return;
    }
    for (ResourceScanner.OptionDomain optionDomain : scanned.optionDomains()) {
      OptionsLoader loader = resolveOptionsLoader(optionDomain.loaderClass());
      if (loader == null) {
        continue;
      }
      String domain = effectiveDomain(optionDomain.domain(), safeDefaultDomain(loader));
      List<ResourceOptionItem> items;
      try {
        items = loader.load(domain, context);
      } catch (RuntimeException ex) {
        items = List.of();
      }
      resources.putOptions(domain, items == null ? List.of() : items);
    }
  }

  private Map<ResourceScanner.ReferenceKey, LinkedHashSet<ResourceScanner.Reference>>
      mergeReferences(ResourceScanner.Result scanned, ResourceCollector collector) {
    Map<ResourceScanner.ReferenceKey, LinkedHashSet<ResourceScanner.Reference>> merged =
        new LinkedHashMap<>();
    appendReferences(merged, scanned);
    if (collector != null) {
      appendReferences(merged, collector.result());
    }
    return merged;
  }

  private void appendReferences(
      Map<ResourceScanner.ReferenceKey, LinkedHashSet<ResourceScanner.Reference>> target,
      ResourceScanner.Result result) {
    if (result == null) {
      return;
    }
    result
        .references()
        .forEach(
            (key, group) ->
                target
                    .computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                    .addAll(group.references()));
  }

  private Set<String> keysOf(Set<ResourceScanner.Reference> references) {
    Set<String> keys = new LinkedHashSet<>();
    for (ResourceScanner.Reference reference : references) {
      if (reference.key() != null) {
        keys.add(reference.key());
      }
    }
    return keys;
  }

  private String resourceKeyOf(ResourceScanner.Reference reference) {
    if (reference.key() != null) {
      return reference.key();
    }
    return reference.onNull() == OnNull.PLACEHOLDER ? NULL_REFERENCE_KEY : null;
  }

  private String effectiveDomain(String domain, String defaultDomain) {
    if (domain != null && !domain.isBlank()) {
      return domain;
    }
    return defaultDomain == null ? "" : defaultDomain;
  }

  private Map<String, Object> emptyIfNull(Map<String, Object> values) {
    return values == null ? Map.of() : values;
  }

  private ResourceLoader<?> resolveResourceLoader(Class<? extends ResourceLoader<?>> loaderClass) {
    List<ResourceLoader<?>> matches = new ArrayList<>();
    for (ResourceLoader<?> loader : resourceLoaders) {
      if (loaderClass.isAssignableFrom(loader.getClass())) {
        matches.add(loader);
      }
    }
    return singleMatch(loaderClass, matches);
  }

  private OptionsLoader resolveOptionsLoader(Class<? extends OptionsLoader> loaderClass) {
    List<OptionsLoader> matches = new ArrayList<>();
    for (OptionsLoader loader : optionsLoaders) {
      if (loaderClass.isAssignableFrom(loader.getClass())) {
        matches.add(loader);
      }
    }
    return singleMatch(loaderClass, matches);
  }

  private <T> T singleMatch(Class<?> loaderClass, List<T> matches) {
    if (matches.isEmpty()) {
      return null;
    }
    if (matches.size() == 1) {
      return matches.get(0);
    }
    String candidates =
        matches.stream().map(match -> match.getClass().getName()).sorted().toList().toString();
    throw new IllegalStateException(
        "Multiple resource loader candidates for " + loaderClass.getName() + ": " + candidates);
  }
}
