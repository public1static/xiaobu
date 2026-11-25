package com.aegisid.foundation.web.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResourceScannerTest {

  private final ResourceScanner scanner = new ResourceScanner();

  @Test
  void scansScalarCollectionArrayNestedMapKeysAndOptionDomains() {
    ResponseDto response =
        new ResponseDto(
            "user-1",
            List.of("role-admin", "role-viewer", "role-admin"),
            new LinkedHashSet<>(List.of("team-a", "team-b")),
            new String[] {"group-a", "group-b", "group-a"},
            new NestedDto("owner-1"),
            orderedMap(),
            new NestedDto("ignored-owner"),
            List.of(new NestedDto("owner-2"), new NestedDto("owner-1")));

    ResourceScanner.Result result = scanner.scan(response);

    assertIterableEquals(
        List.of("user-1", "owner-1", "owner-2"),
        keys(result, UsersLoader.class, "iam-users", "brief"));
    assertIterableEquals(
        List.of("role-admin", "role-viewer"),
        keys(result, RolesLoader.class, "iam-roles", "brief"));
    assertIterableEquals(
        List.of("team-a", "team-b"), keys(result, TeamsLoader.class, "iam-teams", ""));
    assertIterableEquals(
        List.of("group-a", "group-b"), keys(result, GroupsLoader.class, "iam-groups", "label"));
    assertIterableEquals(
        List.of("perm-read", "perm-write"),
        keys(result, PermissionsLoader.class, "iam-permissions", "brief"));
    assertEquals(
        Set.of(new ResourceScanner.OptionDomain(CountriesOptionsLoader.class, "countries")),
        result.optionDomains());
  }

  @Test
  void preservesFallbackAndOnNullForGroupedReferences() {
    StrategyDto response =
        new StrategyDto(
            "user-active",
            "user-deleted",
            mapWithStringKeys("perm-a", 1, "perm-b", 2),
            null,
            "repeatable-key");

    ResourceScanner.Result result = scanner.scan(response);

    ResourceScanner.ReferenceGroup userGroup =
        group(result, UsersLoader.class, "iam-users", "brief");
    assertIterableEquals(
        List.of("user-active", "user-deleted", "repeatable-key"), userGroup.keys());
    assertEquals(
        Set.of(
            new ResourceScanner.Reference(
                "user-active", FallbackStrategy.MISSING_PLACEHOLDER, OnNull.SKIP),
            new ResourceScanner.Reference(
                "user-deleted", FallbackStrategy.LABEL_AS_KEY, OnNull.SKIP),
            new ResourceScanner.Reference(null, FallbackStrategy.DEFAULT, OnNull.PLACEHOLDER),
            new ResourceScanner.Reference("repeatable-key", FallbackStrategy.DEFAULT, OnNull.SKIP)),
        userGroup.references());
    assertIterableEquals(
        List.of("repeatable-key"), group(result, RolesLoader.class, "iam-roles", "compact").keys());

    ResourceScanner.ReferenceGroup permissionGroup =
        group(result, PermissionsLoader.class, "iam-permissions", "brief");
    assertIterableEquals(List.of("perm-a", "perm-b"), permissionGroup.keys());
    assertEquals(
        Set.of(
            new ResourceScanner.Reference("perm-a", FallbackStrategy.SKIP, OnNull.SKIP),
            new ResourceScanner.Reference("perm-b", FallbackStrategy.SKIP, OnNull.SKIP)),
        permissionGroup.references());
  }

  @Test
  void treatsJdkValueObjectsAsLeafValues() {
    UUID userId = UUID.fromString("018f7b38-1dd2-76a7-8dd2-001122334455");
    JdkValuesDto response =
        new JdkValuesDto(userId, new Date(1_700_000_000_000L), UUID.randomUUID());

    ResourceScanner.Result result = scanner.scan(response);

    assertIterableEquals(
        List.of(userId.toString()), keys(result, UsersLoader.class, "iam-users", "brief"));
  }

  private static ResourceScanner.ReferenceGroup group(
      ResourceScanner.Result result,
      Class<? extends ResourceLoader<?>> loaderClass,
      String domain,
      String projection) {
    return result
        .references()
        .get(new ResourceScanner.ReferenceKey(loaderClass, domain, projection));
  }

  private static Set<String> keys(
      ResourceScanner.Result result,
      Class<? extends ResourceLoader<?>> loaderClass,
      String domain,
      String projection) {
    ResourceScanner.ReferenceGroup group = group(result, loaderClass, domain, projection);
    if (group == null) {
      return null;
    }
    return group.keys();
  }

  private static Map<String, Integer> orderedMap() {
    Map<String, Integer> values = new LinkedHashMap<>();
    values.put("perm-read", 10);
    values.put("perm-write", 20);
    return values;
  }

  private static Map<String, Integer> mapWithStringKeys(
      String firstKey, int firstValue, String secondKey, int secondValue) {
    Map<String, Integer> values = new LinkedHashMap<>();
    values.put(firstKey, firstValue);
    values.put(secondKey, secondValue);
    return values;
  }

  private record ResponseDto(
      @ResourceRef(value = UsersLoader.class, domain = "iam-users", projection = "brief")
          String userId,
      @ResourceRef(value = RolesLoader.class, domain = "iam-roles", projection = "brief")
          List<String> roleIds,
      @ResourceRef(value = TeamsLoader.class, domain = "iam-teams") Set<String> teamIds,
      @ResourceRef(value = GroupsLoader.class, domain = "iam-groups", projection = "label")
          String[] groupIds,
      NestedDto owner,
      @ResourceRefMapKey(
              loader = PermissionsLoader.class,
              domain = "iam-permissions",
              projection = "brief")
          Map<String, Integer> permissionWeights,
      @OptionsRef(value = CountriesOptionsLoader.class, domain = "countries") NestedDto optionProbe,
      List<NestedDto> nestedItems) {}

  private record NestedDto(
      @ResourceRef(value = UsersLoader.class, domain = "iam-users", projection = "brief")
          String ownerId) {}

  private record StrategyDto(
      @ResourceRef(
              value = UsersLoader.class,
              domain = "iam-users",
              projection = "brief",
              fallback = FallbackStrategy.MISSING_PLACEHOLDER)
          String activeUserId,
      @ResourceRef(
              value = UsersLoader.class,
              domain = "iam-users",
              projection = "brief",
              fallback = FallbackStrategy.LABEL_AS_KEY)
          String deletedUserId,
      @ResourceRefMapKey(
              loader = PermissionsLoader.class,
              domain = "iam-permissions",
              projection = "brief",
              fallback = FallbackStrategy.SKIP)
          Map<String, Integer> permissionWeights,
      @ResourceRef(
              value = UsersLoader.class,
              domain = "iam-users",
              projection = "brief",
              onNull = OnNull.PLACEHOLDER)
          String missingUserId,
      @ResourceRef(value = UsersLoader.class, domain = "iam-users", projection = "brief")
          @ResourceRef(
              value = RolesLoader.class,
              domain = "iam-roles",
              projection = "compact",
              fallback = FallbackStrategy.LABEL_AS_KEY)
          String repeatableId) {}

  private record JdkValuesDto(
      @ResourceRef(value = UsersLoader.class, domain = "iam-users", projection = "brief")
          UUID userId,
      Date createdAt,
      UUID correlationId) {}

  private static final class UsersLoader implements ResourceLoader<ResourceEnumItem> {
    @Override
    public String resourceType() {
      return "users";
    }

    @Override
    public Map<String, ResourceEnumItem> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      return Map.of();
    }
  }

  private static final class RolesLoader implements ResourceLoader<ResourceEnumItem> {
    @Override
    public String resourceType() {
      return "roles";
    }

    @Override
    public Map<String, ResourceEnumItem> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      return Map.of();
    }
  }

  private static final class TeamsLoader implements ResourceLoader<ResourceEnumItem> {
    @Override
    public String resourceType() {
      return "teams";
    }

    @Override
    public Map<String, ResourceEnumItem> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      return Map.of();
    }
  }

  private static final class GroupsLoader implements ResourceLoader<ResourceEnumItem> {
    @Override
    public String resourceType() {
      return "groups";
    }

    @Override
    public Map<String, ResourceEnumItem> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      return Map.of();
    }
  }

  private static final class PermissionsLoader implements ResourceLoader<ResourceEnumItem> {
    @Override
    public String resourceType() {
      return "permissions";
    }

    @Override
    public Map<String, ResourceEnumItem> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      return Map.of();
    }
  }

  private static final class CountriesOptionsLoader implements OptionsLoader {
    @Override
    public List<ResourceOptionItem> load(String domain, ResourceContext context) {
      return List.of();
    }
  }
}
