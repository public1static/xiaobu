package com.aegisid.foundation.web.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResourceLoadingServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void resourceStatusSerializesLowercaseContractValues() throws Exception {
    assertEquals("active", serializeStatus(ResourceStatus.ACTIVE));
    assertEquals("deleted", serializeStatus(ResourceStatus.DELETED));
    assertEquals("missing", serializeStatus(ResourceStatus.MISSING));
  }

  @Test
  void resourceItemsExposeFactoryDefaultsAndJsonContract() throws Exception {
    ResourceEnumItem enumItem = ResourceEnumItem.labelOnly("Enabled");
    ResourceDictItem dictItem = ResourceDictItem.labelOnly("Asia");
    ResourceOptionItem optionItem = ResourceOptionItem.enabled("CN", "China");
    ResourceEnumItem fullEnumItem =
        new ResourceEnumItem("Pending", "amber", "clock", "Waiting for review");
    ResourceDictItem fullDictItem = new ResourceDictItem("Asia", "blue", "globe", "APAC region");

    assertEquals("Enabled", enumItem.label());
    assertNull(enumItem.color());
    assertNull(enumItem.icon());
    assertNull(enumItem.description());
    assertEquals("Asia", dictItem.label());
    assertNull(dictItem.color());
    assertNull(dictItem.icon());
    assertNull(dictItem.description());
    assertEquals("CN", optionItem.value());
    assertEquals("China", optionItem.label());
    assertNull(optionItem.disabled());
    assertNull(optionItem.reason());

    JsonNode optionJson = toJson(optionItem);
    assertEquals("CN", optionJson.get("value").textValue());
    assertEquals("China", optionJson.get("label").textValue());
    assertFalse(optionJson.has("enabled"));

    JsonNode enumJson = toJson(fullEnumItem);
    assertEquals("Pending", enumJson.get("label").textValue());
    assertEquals("amber", enumJson.get("color").textValue());
    assertEquals("clock", enumJson.get("icon").textValue());
    assertEquals("Waiting for review", enumJson.get("description").textValue());
    assertFalse(enumJson.has("status"));

    JsonNode dictJson = toJson(fullDictItem);
    assertEquals("Asia", dictJson.get("label").textValue());
    assertEquals("blue", dictJson.get("color").textValue());
    assertEquals("globe", dictJson.get("icon").textValue());
    assertEquals("APAC region", dictJson.get("description").textValue());
    assertFalse(dictJson.has("status"));
  }

  @Test
  void resourceContextIsImmutableAndDefensivelyCopiesAuthorities() {
    Set<String> authorities = new LinkedHashSet<>(List.of("iam:user:read"));

    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.SIMPLIFIED_CHINESE, authorities, "req-1");
    authorities.add("iam:user:write");

    assertEquals("tenant-1", context.tenantId());
    assertEquals("user-1", context.currentUserId());
    assertEquals(Locale.SIMPLIFIED_CHINESE, context.locale());
    assertEquals("req-1", context.requestId());
    assertIterableEquals(List.of("iam:user:read"), context.authorities());
    assertThrows(
        UnsupportedOperationException.class, () -> context.authorities().add("iam:user:delete"));
  }

  @Test
  void resourceContextNormalizesNullAuthoritiesToEmptySet() {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, null, "req-null-auth");

    assertTrue(context.authorities().isEmpty());
    assertThrows(
        UnsupportedOperationException.class, () -> context.authorities().add("iam:user:read"));
  }

  @Test
  void fallbackStrategyContainsAllContractValues() {
    assertEquals(FallbackStrategy.DEFAULT, FallbackStrategy.valueOf("DEFAULT"));
    assertEquals(
        FallbackStrategy.MISSING_PLACEHOLDER, FallbackStrategy.valueOf("MISSING_PLACEHOLDER"));
    assertEquals(FallbackStrategy.LABEL_AS_KEY, FallbackStrategy.valueOf("LABEL_AS_KEY"));
    assertEquals(FallbackStrategy.SKIP, FallbackStrategy.valueOf("SKIP"));
  }

  @Test
  void resourceLoaderProvidesDefaultsBasedOnResourceType() {
    ResourceLoader<ResourceEnumItem> enumsLoader =
        new ResourceLoader<>() {
          @Override
          public String resourceType() {
            return "enums";
          }

          @Override
          public Map<String, ResourceEnumItem> load(
              String domain, String projection, Set<String> keys, ResourceContext context) {
            return Map.of();
          }
        };
    ResourceLoader<ResourceDictItem> dictsLoader =
        new ResourceLoader<>() {
          @Override
          public String resourceType() {
            return "dicts";
          }

          @Override
          public Map<String, ResourceDictItem> load(
              String domain, String projection, Set<String> keys, ResourceContext context) {
            return Map.of();
          }
        };
    ResourceLoader<Map<String, Object>> usersLoader =
        new ResourceLoader<>() {
          @Override
          public String resourceType() {
            return "users";
          }

          @Override
          public Map<String, Map<String, Object>> load(
              String domain, String projection, Set<String> keys, ResourceContext context) {
            return Map.of();
          }
        };

    assertEquals("", usersLoader.defaultDomain());
    assertEquals(Duration.ZERO, usersLoader.cacheTtl());
    assertEquals(Duration.ofMillis(300), usersLoader.timeout());
    assertEquals(FallbackStrategy.LABEL_AS_KEY, enumsLoader.fallback());
    assertEquals(FallbackStrategy.LABEL_AS_KEY, dictsLoader.fallback());
    assertEquals(FallbackStrategy.MISSING_PLACEHOLDER, usersLoader.fallback());
  }

  @Test
  void resourceLoaderNewSignatureCarriesDomainAndProjection() {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-1");
    RecordingResourceLoader loader = new RecordingResourceLoader();

    Map<String, ResourceEnumItem> items = loader.load("iam", "brief", Set.of("ACTIVE"), context);

    assertEquals("domain=iam,projection=brief,keys=ACTIVE,requestId=req-1", loader.call);
    assertEquals("Active", items.get("ACTIVE").label());
  }

  @Test
  void resourceLoaderFallbackDoesNotThrowWhenResourceTypeIsNull() {
    ResourceLoader<ResourceEnumItem> loader =
        new ResourceLoader<>() {
          @Override
          public String resourceType() {
            return null;
          }

          @Override
          public Map<String, ResourceEnumItem> load(
              String domain, String projection, Set<String> keys, ResourceContext context) {
            return Map.of();
          }
        };

    assertEquals(FallbackStrategy.MISSING_PLACEHOLDER, loader.fallback());
    assertNull(
        loader.missingPlaceholder(
            "missing-id",
            new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-missing")));
    assertNull(
        loader.deletedPlaceholder(
            "deleted-id",
            new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-deleted")));
  }

  @Test
  void resourceLoaderPlaceholderMethodsReceiveContext() {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-placeholder");
    PlaceholderResourceLoader loader = new PlaceholderResourceLoader();

    ResourceEnumItem missing = loader.missingPlaceholder("user-404", context);
    ResourceEnumItem deleted = loader.deletedPlaceholder("user-410", context);

    assertEquals("missing:user-404:req-placeholder", missing.label());
    assertEquals("deleted:user-410:user-1", deleted.label());
  }

  @Test
  void emptyApiResourcesSnapshotIsImmutableThroughAsMap() {
    ApiResources snapshot = ApiResources.copyOf(ApiResources.empty());

    assertThrows(
        UnsupportedOperationException.class, () -> snapshot.asMap().put("users", Map.of()));
  }

  @Test
  void optionsLoaderIsIndependentDomainListSpi() {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-options");
    RecordingOptionsLoader loader = new RecordingOptionsLoader();

    List<ResourceOptionItem> items = loader.load("countries", context);

    assertEquals("options", loader.resourceType());
    assertEquals("", loader.defaultDomain());
    assertEquals(Duration.ZERO, loader.cacheTtl());
    assertEquals("countries:req-options", loader.call);
    assertEquals(List.of(ResourceOptionItem.enabled("CN", "China")), items);
  }

  @Test
  void resourceLoadingServiceBatchesDedupeFallbacksAndRuntimeOptions() throws Exception {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-load");
    LoadingUsersLoader usersLoader = new LoadingUsersLoader();
    LoadingEnumsLoader enumsLoader = new LoadingEnumsLoader();
    LoadingDictsLoader dictsLoader = new LoadingDictsLoader();
    LoadingOptionsLoader optionsLoader = new LoadingOptionsLoader();
    ResourceLoadingService service =
        new ResourceLoadingService(
            List.of(usersLoader, enumsLoader, dictsLoader), List.of(optionsLoader));
    ResourceScanner.Result scanned =
        new ResourceScanner()
            .scan(
                new LoadingResponse(
                    "user-1",
                    List.of("user-2", "user-missing", "user-1"),
                    new LoadingPage(
                        List.of(new LoadingItem("user-2"), new LoadingItem("user-deleted"))),
                    "PENDING",
                    "UNKNOWN",
                    "CN"));
    ResourceCollector collector =
        new DefaultResourceCollector()
            .ref(LoadingUsersLoader.class, "iam-users", "brief", "user-3")
            .refs(LoadingUsersLoader.class, "iam-users", "brief", List.of("user-2", "user-3"))
            .options(
                "runtime-statuses",
                List.of(
                    ResourceOptionItem.enabled("DRAFT", "Draft"),
                    ResourceOptionItem.enabled("LIVE", "Live")));

    ApiResources resources = service.load(scanned, collector, context);

    assertEquals(
        List.of("iam-users:brief:user-1,user-2,user-missing,user-deleted,user-3"),
        usersLoader.calls);
    assertEquals(List.of("account-status::PENDING"), enumsLoader.calls);
    assertEquals(List.of("regions::UNKNOWN"), dictsLoader.calls);
    assertEquals(List.of("countries"), optionsLoader.calls);

    JsonNode json = toJson(resources);
    assertEquals("User One", json.at("/users/user-1/displayName").textValue());
    assertEquals("missing", json.at("/users/user-missing/status").textValue());
    assertEquals("user-missing", json.at("/users/user-missing/id").textValue());
    assertEquals("user-missing", json.at("/users/user-missing/displayName").textValue());
    assertEquals("deleted", json.at("/users/user-deleted/status").textValue());
    assertEquals("user-deleted", json.at("/users/user-deleted/id").textValue());
    assertEquals("Deleted user", json.at("/users/user-deleted/displayName").textValue());
    assertEquals("PENDING", json.at("/enums/account-status/PENDING/label").textValue());
    assertEquals("UNKNOWN", json.at("/dicts/regions/UNKNOWN/label").textValue());
    assertEquals("CN", json.at("/options/countries/0/value").textValue());
    assertEquals("US", json.at("/options/countries/1/value").textValue());
    assertEquals("DRAFT", json.at("/options/runtime-statuses/0/value").textValue());
    assertEquals("LIVE", json.at("/options/runtime-statuses/1/value").textValue());
  }

  @Test
  void resourceLoadingServiceFallsBackWhenLoaderThrowsAndSkipsWhenRequested() throws Exception {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-fail");
    ThrowingUsersLoader usersLoader = new ThrowingUsersLoader();
    LoadingEnumsLoader enumsLoader = new LoadingEnumsLoader();
    ResourceLoadingService service =
        new ResourceLoadingService(List.of(usersLoader, enumsLoader), List.of());
    ResourceScanner.Result scanned =
        new ResourceScanner().scan(new FailureResponse("user-failed", "user-skipped", "PENDING"));

    ApiResources resources = service.load(scanned, new DefaultResourceCollector(), context);

    JsonNode json = toJson(resources);
    assertEquals(List.of("iam-users:brief:user-failed,user-skipped"), usersLoader.calls);
    assertEquals("missing", json.at("/users/user-failed/status").textValue());
    assertEquals("user-failed", json.at("/users/user-failed/displayName").textValue());
    assertTrue(json.at("/users/user-skipped").isMissingNode());
    assertEquals("PENDING", json.at("/enums/account-status/PENDING/label").textValue());
  }

  @Test
  void resourceLoadingServiceFallsBackWhenLoaderExceedsTimeout() throws Exception {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-timeout");
    SlowUsersLoader usersLoader = new SlowUsersLoader();
    ResourceLoadingService service = new ResourceLoadingService(List.of(usersLoader), List.of());
    ResourceCollector collector =
        new DefaultResourceCollector()
            .ref(SlowUsersLoader.class, "iam-users", "brief", "slow-user");

    ApiResources resources = service.load(new ResourceScanner().scan(null), collector, context);

    JsonNode json = toJson(resources);
    assertTrue(usersLoader.started.await(500, TimeUnit.MILLISECONDS));
    assertEquals("missing", json.at("/users/slow-user/status").textValue());
    assertEquals("slow-user", json.at("/users/slow-user/displayName").textValue());
  }

  @Test
  void resourceLoadingServiceUsesBoundedExecutorForTimedOutLoaders() throws Exception {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-bounded");
    StubbornUsersLoader usersLoader = new StubbornUsersLoader();
    AtomicInteger createdThreads = new AtomicInteger();
    ThreadFactory threadFactory =
        runnable -> {
          Thread thread =
              new Thread(runnable, "test-resource-loader-" + createdThreads.incrementAndGet());
          thread.setDaemon(true);
          return thread;
        };
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            threadFactory,
            new ThreadPoolExecutor.AbortPolicy());
    ResourceLoadingService service =
        new ResourceLoadingService(List.of(usersLoader), List.of(), executor);
    ResourceCollector collector =
        new DefaultResourceCollector()
            .ref(StubbornUsersLoader.class, "iam-users", "brief", "stubborn-user");

    try {
      List<ApiResources> results = new ArrayList<>();
      long startedAt = System.nanoTime();
      for (int index = 0; index < 5; index++) {
        results.add(service.load(new ResourceScanner().scan(null), collector, context));
      }
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

      assertTrue(usersLoader.started.await(500, TimeUnit.MILLISECONDS));
      assertTrue(elapsedMillis < 1_000, "timed-out loader must not hold the main response");
      assertEquals(1, createdThreads.get());
      for (ApiResources resources : results) {
        JsonNode json = toJson(resources);
        assertEquals("missing", json.at("/users/stubborn-user/status").textValue());
        assertEquals("stubborn-user", json.at("/users/stubborn-user/displayName").textValue());
      }
    } finally {
      usersLoader.release();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }
  }

  @Test
  void resourceLoadingServiceFallsBackWhenLoaderExecutorRejects() throws Exception {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-rejected");
    LoadingUsersLoader usersLoader = new LoadingUsersLoader();
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1));
    executor.shutdown();
    ResourceLoadingService service =
        new ResourceLoadingService(List.of(usersLoader), List.of(), executor);
    ResourceCollector collector =
        new DefaultResourceCollector()
            .ref(LoadingUsersLoader.class, "iam-users", "brief", "rejected-user");

    ApiResources resources = service.load(new ResourceScanner().scan(null), collector, context);

    JsonNode json = toJson(resources);
    assertTrue(usersLoader.calls.isEmpty());
    assertEquals("missing", json.at("/users/rejected-user/status").textValue());
    assertEquals("rejected-user", json.at("/users/rejected-user/displayName").textValue());
  }

  @Test
  void resourceCollectorCanInjectPolymorphicReferencesAndOptions() throws Exception {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-dynamic");
    LoadingUsersLoader usersLoader = new LoadingUsersLoader();
    ResourceLoadingService service = new ResourceLoadingService(List.of(usersLoader), List.of());
    ResourceCollector collector =
        new DefaultResourceCollector()
            .ref(LoadingUsersLoader.class, "iam-users", "compact", "dynamic-owner")
            .options(
                "dynamic-actions",
                List.of(
                    ResourceOptionItem.enabled("disable", "Disable"),
                    ResourceOptionItem.enabled("delete", "Delete")));

    ApiResources resources = service.load(new ResourceScanner().scan(null), collector, context);

    JsonNode json = toJson(resources);
    assertEquals(List.of("iam-users:compact:dynamic-owner"), usersLoader.calls);
    assertEquals("Dynamic Owner", json.at("/users/dynamic-owner/displayName").textValue());
    assertEquals("disable", json.at("/options/dynamic-actions/0/value").textValue());
    assertEquals("delete", json.at("/options/dynamic-actions/1/value").textValue());
  }

  @Test
  void resourceLoadingServiceResolvesAssignableLoaderClasses() throws Exception {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-proxy");
    ProxyUsersLoader usersLoader = new ProxyUsersLoader();
    InterfaceEnumsLoader enumsLoader = new InterfaceEnumsLoader();
    ResourceLoadingService service =
        new ResourceLoadingService(List.of(usersLoader, enumsLoader), List.of());
    ResourceCollector collector =
        new DefaultResourceCollector()
            .ref(ParentUsersLoader.class, "iam-users", "brief", "proxy-user")
            .ref(InterfaceResourceLoader.class, "statuses", "", "ACTIVE");

    ApiResources resources = service.load(new ResourceScanner().scan(null), collector, context);

    JsonNode json = toJson(resources);
    assertEquals("Proxy User", json.at("/users/proxy-user/displayName").textValue());
    assertEquals("Active", json.at("/enums/statuses/ACTIVE/label").textValue());
  }

  @Test
  void resourceLoadingServiceRejectsDuplicateAssignableLoaderCandidates() {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-duplicate");
    ResourceLoadingService service =
        new ResourceLoadingService(
            List.of(new FirstDuplicateUsersLoader(), new SecondDuplicateUsersLoader()), List.of());
    ResourceCollector collector =
        new DefaultResourceCollector()
            .ref(DuplicateParentUsersLoader.class, "iam-users", "brief", "duplicate-user");

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> service.load(new ResourceScanner().scan(null), collector, context));

    assertTrue(error.getMessage().contains(DuplicateParentUsersLoader.class.getName()));
  }

  @Test
  void resourceLoadingServiceFallsBackWhenLoaderHooksThrow() throws Exception {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-hooks");
    ThrowingHooksUsersLoader loader = new ThrowingHooksUsersLoader();
    ResourceLoadingService service = new ResourceLoadingService(List.of(loader), List.of());
    ResourceCollector collector =
        new DefaultResourceCollector()
            .refs(ThrowingHooksUsersLoader.class, List.of("hook-missing", "hook-deleted"));

    ApiResources resources = service.load(new ResourceScanner().scan(null), collector, context);

    JsonNode json = toJson(resources);
    assertEquals(List.of(":hook-missing,hook-deleted"), loader.calls);
    assertEquals("missing", json.at("/users/hook-missing/status").textValue());
    assertEquals("hook-missing", json.at("/users/hook-missing/displayName").textValue());
    assertEquals("deleted", json.at("/users/hook-deleted/status").textValue());
    assertEquals("hook-deleted", json.at("/users/hook-deleted/displayName").textValue());
  }

  @Test
  void resourceLoadingServiceSurvivesThrowingResourceType() throws Exception {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-type");
    ResourceLoadingService service =
        new ResourceLoadingService(List.of(new ThrowingTypeLoader()), List.of());
    ResourceCollector collector =
        new DefaultResourceCollector().ref(ThrowingTypeLoader.class, "", "", "type-missing");

    ApiResources resources = service.load(new ResourceScanner().scan(null), collector, context);

    JsonNode json = toJson(resources);
    assertEquals("missing", json.at("/resources/type-missing/status").textValue());
    assertEquals("type-missing", json.at("/resources/type-missing/displayName").textValue());
  }

  @Test
  void resourceLoadingServiceEmitsPlaceholderForNullReference() throws Exception {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-null");
    LoadingUsersLoader usersLoader = new LoadingUsersLoader();
    ResourceLoadingService service = new ResourceLoadingService(List.of(usersLoader), List.of());
    ResourceScanner.Result scanned = new ResourceScanner().scan(new NullPlaceholderResponse(null));

    ApiResources resources = service.load(scanned, new DefaultResourceCollector(), context);

    JsonNode json = toJson(resources);
    assertTrue(usersLoader.calls.isEmpty());
    assertEquals("_null", json.at("/users/_null/id").textValue());
    assertEquals("_null", json.at("/users/_null/displayName").textValue());
    assertEquals("missing", json.at("/users/_null/status").textValue());
  }

  @Test
  void emptyResourceLoadingResultIsImmutableThroughAsMap() {
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-empty");
    ResourceLoadingService service = new ResourceLoadingService(List.of(), List.of());

    ApiResources resources =
        service.load(new ResourceScanner().scan(null), new DefaultResourceCollector(), context);

    assertThrows(
        UnsupportedOperationException.class, () -> resources.asMap().put("users", Map.of()));
  }

  @Test
  void localFirstResourceLoaderLoadsLocalFirstThenRemoteForMissesOnly() {
    RecordingLocalFirstLoader loader = new RecordingLocalFirstLoader();
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-1");

    Map<String, ResourceEnumItem> items =
        loader.load("iam", "brief", orderedSet("ACTIVE", "DELETED", "MISSING"), context);

    assertEquals(
        List.of("local:iam:brief:ACTIVE,DELETED,MISSING", "remote:iam:brief:DELETED,MISSING"),
        loader.calls);
    assertEquals("Active", items.get("ACTIVE").label());
    assertEquals("Deleted", items.get("DELETED").label());
    assertEquals("Missing", items.get("MISSING").label());
  }

  @Test
  void localFirstResourceLoaderEmptyKeysDoesNotCallLocalOrRemote() {
    RecordingLocalFirstLoader loader = new RecordingLocalFirstLoader();
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-1");

    Map<String, ResourceEnumItem> items = loader.load("iam", "brief", Set.of(), context);

    assertTrue(items.isEmpty());
    assertTrue(loader.calls.isEmpty());
  }

  @Test
  void localFirstResourceLoaderRequiresKeysAndContext() {
    RecordingLocalFirstLoader loader = new RecordingLocalFirstLoader();
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-1");

    assertThrows(NullPointerException.class, () -> loader.load("iam", "brief", null, context));
    assertThrows(
        NullPointerException.class, () -> loader.load("iam", "brief", Set.of("ACTIVE"), null));
  }

  @Test
  void localFirstResourceLoaderTreatsNullLocalAndRemoteAsEmptyMaps() {
    NullReturningLocalFirstLoader loader = new NullReturningLocalFirstLoader();
    ResourceContext context =
        new ResourceContext("tenant-1", "user-1", Locale.US, Set.of(), "req-1");

    Map<String, ResourceEnumItem> items =
        loader.load("iam", "brief", orderedSet("ACTIVE"), context);

    assertTrue(items.isEmpty());
    assertEquals(List.of("local", "remote"), loader.calls);
  }

  private String serializeStatus(ResourceStatus status) throws Exception {
    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(status));
    return json.textValue();
  }

  private JsonNode toJson(Object value) throws Exception {
    return objectMapper.readTree(objectMapper.writeValueAsString(value));
  }

  private static Set<String> orderedSet(String... keys) {
    return new LinkedHashSet<>(List.of(keys));
  }

  private record LoadingResponse(
      @ResourceRef(value = LoadingUsersLoader.class, domain = "iam-users", projection = "brief")
          String ownerId,
      @ResourceRef(value = LoadingUsersLoader.class, domain = "iam-users", projection = "brief")
          List<String> reviewerIds,
      LoadingPage page,
      @ResourceRef(value = LoadingEnumsLoader.class, domain = "account-status") String statusCode,
      @ResourceRef(value = LoadingDictsLoader.class, domain = "regions") String regionCode,
      @OptionsRef(value = LoadingOptionsLoader.class, domain = "countries") String countryCode) {}

  private record LoadingPage(List<LoadingItem> items) {}

  private record LoadingItem(
      @ResourceRef(value = LoadingUsersLoader.class, domain = "iam-users", projection = "brief")
          String assigneeId) {}

  private record FailureResponse(
      @ResourceRef(
              value = ThrowingUsersLoader.class,
              domain = "iam-users",
              projection = "brief",
              fallback = FallbackStrategy.MISSING_PLACEHOLDER)
          String missingUserId,
      @ResourceRef(
              value = ThrowingUsersLoader.class,
              domain = "iam-users",
              projection = "brief",
              fallback = FallbackStrategy.SKIP)
          String skippedUserId,
      @ResourceRef(value = LoadingEnumsLoader.class, domain = "account-status")
          String statusCode) {}

  private record NullPlaceholderResponse(
      @ResourceRef(
              value = LoadingUsersLoader.class,
              domain = "iam-users",
              projection = "brief",
              onNull = OnNull.PLACEHOLDER)
          String ownerId) {}

  private static Map<String, Object> userResource(
      String id, String displayName, ResourceStatus status) {
    Map<String, Object> resource = new LinkedHashMap<>();
    resource.put("id", id);
    resource.put("displayName", displayName);
    resource.put("status", status);
    return resource;
  }

  private static final class LoadingUsersLoader implements ResourceLoader<Map<String, Object>> {

    private final List<String> calls = new ArrayList<>();

    @Override
    public String resourceType() {
      return "users";
    }

    @Override
    public Map<String, Map<String, Object>> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      calls.add("%s:%s:%s".formatted(domain, projection, String.join(",", keys)));
      Map<String, Map<String, Object>> loaded = new LinkedHashMap<>();
      if (keys.contains("user-1")) {
        loaded.put("user-1", userResource("user-1", "User One", ResourceStatus.ACTIVE));
      }
      if (keys.contains("user-2")) {
        loaded.put("user-2", userResource("user-2", "User Two", ResourceStatus.ACTIVE));
      }
      if (keys.contains("user-3")) {
        loaded.put("user-3", userResource("user-3", "User Three", ResourceStatus.ACTIVE));
      }
      if (keys.contains("dynamic-owner")) {
        loaded.put(
            "dynamic-owner", userResource("dynamic-owner", "Dynamic Owner", ResourceStatus.ACTIVE));
      }
      if (keys.contains("user-deleted")) {
        loaded.put(
            "user-deleted", userResource("user-deleted", "Former user", ResourceStatus.DELETED));
      }
      return loaded;
    }

    @Override
    public Map<String, Object> deletedPlaceholder(String key, ResourceContext context) {
      return userResource(key, "Deleted user", ResourceStatus.DELETED);
    }
  }

  private static final class ThrowingUsersLoader implements ResourceLoader<Map<String, Object>> {

    private final List<String> calls = new ArrayList<>();

    @Override
    public String resourceType() {
      return "users";
    }

    @Override
    public Map<String, Map<String, Object>> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      calls.add("%s:%s:%s".formatted(domain, projection, String.join(",", keys)));
      throw new IllegalStateException("load failed");
    }
  }

  private static final class SlowUsersLoader implements ResourceLoader<Map<String, Object>> {

    private final CountDownLatch started = new CountDownLatch(1);

    @Override
    public String resourceType() {
      return "users";
    }

    @Override
    public Duration timeout() {
      return Duration.ofMillis(25);
    }

    @Override
    public Map<String, Map<String, Object>> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      started.countDown();
      try {
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      return Map.of("slow-user", userResource("slow-user", "Slow User", ResourceStatus.ACTIVE));
    }
  }

  private static final class StubbornUsersLoader implements ResourceLoader<Map<String, Object>> {

    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Override
    public String resourceType() {
      return "users";
    }

    @Override
    public Duration timeout() {
      return Duration.ofMillis(25);
    }

    @Override
    public Map<String, Map<String, Object>> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      started.countDown();
      while (release.getCount() > 0) {
        try {
          release.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
          // Simulates a misbehaving loader that does not honor cancellation.
        }
      }
      return Map.of(
          "stubborn-user", userResource("stubborn-user", "Stubborn User", ResourceStatus.ACTIVE));
    }

    private void release() {
      release.countDown();
    }
  }

  private static final class LoadingEnumsLoader implements ResourceLoader<ResourceEnumItem> {

    private final List<String> calls = new ArrayList<>();

    @Override
    public String resourceType() {
      return "enums";
    }

    @Override
    public Map<String, ResourceEnumItem> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      calls.add("%s:%s:%s".formatted(domain, projection, String.join(",", keys)));
      return Map.of();
    }
  }

  private static final class LoadingDictsLoader implements ResourceLoader<ResourceDictItem> {

    private final List<String> calls = new ArrayList<>();

    @Override
    public String resourceType() {
      return "dicts";
    }

    @Override
    public Map<String, ResourceDictItem> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      calls.add("%s:%s:%s".formatted(domain, projection, String.join(",", keys)));
      return Map.of();
    }
  }

  private static final class LoadingOptionsLoader implements OptionsLoader {

    private final List<String> calls = new ArrayList<>();

    @Override
    public List<ResourceOptionItem> load(String domain, ResourceContext context) {
      calls.add(domain);
      return List.of(
          ResourceOptionItem.enabled("CN", "China"),
          ResourceOptionItem.enabled("US", "United States"));
    }
  }

  private static class ParentUsersLoader implements ResourceLoader<Map<String, Object>> {

    @Override
    public String resourceType() {
      return "users";
    }

    @Override
    public Map<String, Map<String, Object>> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      return Map.of();
    }
  }

  private static final class ProxyUsersLoader extends ParentUsersLoader {

    @Override
    public Map<String, Map<String, Object>> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      return Map.of("proxy-user", userResource("proxy-user", "Proxy User", ResourceStatus.ACTIVE));
    }
  }

  private interface InterfaceResourceLoader extends ResourceLoader<ResourceEnumItem> {}

  private static final class InterfaceEnumsLoader implements InterfaceResourceLoader {

    @Override
    public String resourceType() {
      return "enums";
    }

    @Override
    public Map<String, ResourceEnumItem> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      return Map.of("ACTIVE", ResourceEnumItem.labelOnly("Active"));
    }
  }

  private static class DuplicateParentUsersLoader implements ResourceLoader<Map<String, Object>> {

    @Override
    public String resourceType() {
      return "users";
    }

    @Override
    public Map<String, Map<String, Object>> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      return Map.of();
    }
  }

  private static final class FirstDuplicateUsersLoader extends DuplicateParentUsersLoader {}

  private static final class SecondDuplicateUsersLoader extends DuplicateParentUsersLoader {}

  private static final class ThrowingHooksUsersLoader
      implements ResourceLoader<Map<String, Object>> {

    private final List<String> calls = new ArrayList<>();

    @Override
    public String resourceType() {
      return "users";
    }

    @Override
    public String defaultDomain() {
      throw new IllegalStateException("domain failed");
    }

    @Override
    public Map<String, Map<String, Object>> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      calls.add("%s:%s".formatted(domain, String.join(",", keys)));
      return Map.of("hook-deleted", userResource("hook-deleted", "Former", ResourceStatus.DELETED));
    }

    @Override
    public FallbackStrategy fallback() {
      throw new IllegalStateException("fallback failed");
    }

    @Override
    public Map<String, Object> missingPlaceholder(String key, ResourceContext context) {
      throw new IllegalStateException("missing failed");
    }

    @Override
    public Map<String, Object> deletedPlaceholder(String key, ResourceContext context) {
      throw new IllegalStateException("deleted failed");
    }
  }

  private static final class ThrowingTypeLoader implements ResourceLoader<Map<String, Object>> {

    @Override
    public String resourceType() {
      throw new IllegalStateException("type failed");
    }

    @Override
    public Map<String, Map<String, Object>> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      return Map.of();
    }
  }

  private static final class RecordingResourceLoader implements ResourceLoader<ResourceEnumItem> {

    private String call;

    @Override
    public String resourceType() {
      return "enums";
    }

    @Override
    public Map<String, ResourceEnumItem> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      call =
          "domain=%s,projection=%s,keys=%s,requestId=%s"
              .formatted(domain, projection, String.join(",", keys), context.requestId());
      return Map.of("ACTIVE", ResourceEnumItem.labelOnly("Active"));
    }
  }

  private static final class PlaceholderResourceLoader implements ResourceLoader<ResourceEnumItem> {

    @Override
    public String resourceType() {
      return "users";
    }

    @Override
    public Map<String, ResourceEnumItem> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      return Map.of();
    }

    @Override
    public ResourceEnumItem missingPlaceholder(String key, ResourceContext context) {
      return ResourceEnumItem.labelOnly("missing:%s:%s".formatted(key, context.requestId()));
    }

    @Override
    public ResourceEnumItem deletedPlaceholder(String key, ResourceContext context) {
      return ResourceEnumItem.labelOnly("deleted:%s:%s".formatted(key, context.currentUserId()));
    }
  }

  private static final class RecordingOptionsLoader implements OptionsLoader {

    private String call;

    @Override
    public List<ResourceOptionItem> load(String domain, ResourceContext context) {
      call = domain + ":" + context.requestId();
      return List.of(ResourceOptionItem.enabled("CN", "China"));
    }
  }

  private static final class RecordingLocalFirstLoader
      extends LocalFirstResourceLoader<ResourceEnumItem> {

    private final List<String> calls = new ArrayList<>();

    @Override
    public String resourceType() {
      return "enums";
    }

    @Override
    protected Map<String, ResourceEnumItem> tryLocal(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      calls.add("local:%s:%s:%s".formatted(domain, projection, String.join(",", keys)));
      Map<String, ResourceEnumItem> local = new LinkedHashMap<>();
      if (keys.contains("ACTIVE")) {
        local.put("ACTIVE", ResourceEnumItem.labelOnly("Active"));
      }
      return local;
    }

    @Override
    protected Map<String, ResourceEnumItem> tryRemote(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      calls.add("remote:%s:%s:%s".formatted(domain, projection, String.join(",", keys)));
      Map<String, ResourceEnumItem> remote = new LinkedHashMap<>();
      if (keys.contains("DELETED")) {
        remote.put("DELETED", ResourceEnumItem.labelOnly("Deleted"));
      }
      if (keys.contains("MISSING")) {
        remote.put("MISSING", ResourceEnumItem.labelOnly("Missing"));
      }
      return remote;
    }
  }

  private static final class NullReturningLocalFirstLoader
      extends LocalFirstResourceLoader<ResourceEnumItem> {

    private final List<String> calls = new ArrayList<>();

    @Override
    public String resourceType() {
      return "users";
    }

    @Override
    protected Map<String, ResourceEnumItem> tryLocal(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      calls.add("local");
      return null;
    }

    @Override
    protected Map<String, ResourceEnumItem> tryRemote(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      calls.add("remote");
      return null;
    }
  }
}
