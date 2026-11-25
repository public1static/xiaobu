package com.aegisid.foundation.web.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisid.foundation.web.api.ApiErrorResponse;
import com.aegisid.foundation.web.api.ApiResponse;
import com.aegisid.foundation.web.api.PageResponse;
import com.aegisid.foundation.web.error.GlobalExceptionHandler;
import com.aegisid.foundation.web.request.RequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

class ApiResponseResourcesContractTest {

  private static final Instant TIMESTAMP = Instant.parse("2026-04-25T10:00:00Z");

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final CountingUserLoader userLoader = new CountingUserLoader();
  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(new ContractController())
          .setControllerAdvice(
              new ResourceEnvelopeAdvice(
                  new ResourceScanner(),
                  new ResourceLoadingService(List.of(userLoader), List.of())),
              new GlobalExceptionHandler())
          .setMessageConverters(new JacksonJsonHttpMessageConverter())
          .build();

  @Test
  void getDefaultsToLoadedResourcesWhenIncludeResourcesIsMissing() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/explicit-users/user-1").with(requestContext("req-default")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode json = responseJson(result);

    assertTrue(json.get("success").booleanValue());
    assertEquals("OK", json.get("code").textValue());
    assertEquals(ApiResponse.SUCCESS_MESSAGE, json.get("message").textValue());
    assertEquals("user-1", json.get("data").get("id").textValue());
    assertEquals("user-1", json.get("data").get("ownerId").textValue());
    assertEquals(
        "User user-1",
        json.get("resources").get("users").get("user-1").get("displayName").textValue());
    assertEquals("req-default", json.get("requestId").textValue());
    assertTrue(json.get("timestamp").isTextual());

    assertEquals(1, userLoader.calls());
  }

  @Test
  void explicitApiResponseLoadsResourcesFromData() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/explicit-users/user-explicit").with(requestContext("req-explicit")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode json = responseJson(result);

    assertTrue(json.get("success").booleanValue());
    assertEquals(ApiResponse.SUCCESS_CODE, json.get("code").textValue());
    assertEquals(ApiResponse.SUCCESS_MESSAGE, json.get("message").textValue());
    assertEquals("user-explicit", json.get("data").get("id").textValue());
    assertEquals(
        "User user-explicit",
        json.path("resources").path("users").path("user-explicit").path("displayName").textValue());
    assertEquals("req-explicit", json.get("requestId").textValue());
  }

  @Test
  void explicitApiResponseLoadsResourcesFromPageItems() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/explicit-user-page").with(requestContext("req-page")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode json = responseJson(result);

    assertEquals("page-user", json.get("data").get("items").get(0).get("id").textValue());
    assertEquals(
        "User page-user",
        json.path("resources").path("users").path("page-user").path("displayName").textValue());
    assertFalse(json.get("data").has("resources"));
  }

  @Test
  void bareBusinessObjectIsNotWrappedByResourceEnvelopeAdvice() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/users/user-bare").with(requestContext("req-bare")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode json = responseJson(result);

    assertEquals("user-bare", json.path("id").textValue());
    assertFalse(json.has("success"));
    assertFalse(json.has("resources"));
    assertEquals(0, userLoader.calls());
  }

  @Test
  void getIncludeResourcesFalseReturnsEmptyResourcesAndSkipsLoader() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/explicit-users/user-2")
                    .queryParam("includeResources", "false")
                    .with(requestContext("req-off")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode json = responseJson(result);

    assertEquals("user-2", json.get("data").get("id").textValue());
    assertTrue(json.get("resources").isObject());
    assertFalse(json.get("resources").has("users"));
    assertEquals("req-off", json.get("requestId").textValue());

    assertEquals(0, userLoader.calls());
  }

  @Test
  void invalidIncludeResourcesValuesAreTreatedAsTrue() throws Exception {
    for (String value : List.of("yes", "1", "abc")) {
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/explicit-users/{id}", "user-" + value)
                      .queryParam("includeResources", value)
                      .with(requestContext("req-" + value)))
              .andExpect(status().isOk())
              .andReturn();
      JsonNode json = responseJson(result);
      assertEquals(
          "User user-" + value,
          json.get("resources").get("users").get("user-" + value).get("displayName").textValue());
    }

    assertEquals(3, userLoader.calls());
  }

  @Test
  void noContentResponseIsNotWrapped() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/no-content").with(requestContext("req-no-content")))
            .andExpect(status().isNoContent())
            .andReturn();

    assertEquals("", result.getResponse().getContentAsString());
    assertEquals(0, userLoader.calls());
  }

  @Test
  void oauthAndOidcProtocolEndpointsAreNotWrapped() throws Exception {
    MvcResult oidc =
        mockMvc
            .perform(get("/.well-known/openid-configuration").with(requestContext("req-oidc")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode oidcJson = responseJson(oidc);
    assertEquals("https://issuer.example", oidcJson.get("issuer").textValue());
    assertFalse(oidcJson.has("success"));
    assertFalse(oidcJson.has("resources"));

    MvcResult oauth2 =
        mockMvc
            .perform(get("/oauth2/authorize").with(requestContext("req-oauth2")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode oauth2Json = responseJson(oauth2);
    assertEquals("/oauth2/authorize", oauth2Json.get("authorization_endpoint").textValue());
    assertFalse(oauth2Json.has("success"));
    assertFalse(oauth2Json.has("resources"));

    MvcResult login =
        mockMvc
            .perform(get("/login/oauth2/code/aegisid").with(requestContext("req-login-oauth2")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode loginJson = responseJson(login);
    assertEquals("aegisid", loginJson.get("registrationId").textValue());
    assertFalse(loginJson.has("success"));
    assertFalse(loginJson.has("resources"));

    assertEquals(0, userLoader.calls());
  }

  @Test
  void errorResponsesContainEmptyResources() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/error").with(requestContext("req-error")))
            .andExpect(status().isInternalServerError())
            .andReturn();
    JsonNode json = responseJson(result);

    assertFalse(json.get("success").booleanValue());
    assertEquals(GlobalExceptionHandler.INTERNAL_ERROR_CODE, json.get("code").textValue());
    assertTrue(json.get("data").isNull());
    assertTrue(json.get("resources").isObject());
    assertFalse(json.get("resources").has("users"));
    assertEquals("req-error", json.get("requestId").textValue());

    assertEquals(0, userLoader.calls());
  }

  @Test
  void non2xxResponseEntityBodyIsNotWrappedAsSuccess() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/not-found").with(requestContext("req-not-found")))
            .andExpect(status().isNotFound())
            .andReturn();
    JsonNode json = responseJson(result);

    assertEquals("USER_NOT_FOUND", json.get("code").textValue());
    assertFalse(json.has("success"));
    assertFalse(json.has("resources"));
    assertEquals(0, userLoader.calls());
  }

  @Test
  void problemDetailIsNotWrappedAsSuccess() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/problem").with(requestContext("req-problem")))
            .andExpect(status().isBadRequest())
            .andReturn();
    JsonNode json = responseJson(result);

    assertEquals(400, json.get("status").intValue());
    assertEquals("Invalid request", json.get("detail").textValue());
    assertFalse(json.has("success"));
    assertFalse(json.has("resources"));
    assertEquals(0, userLoader.calls());
  }

  @Test
  void headRequestDoesNotWrapOrLoadResources() throws Exception {
    MvcResult result =
        mockMvc
            .perform(head("/api/users/user-head").with(requestContext("req-head")))
            .andExpect(status().isOk())
            .andReturn();

    assertEquals("", result.getResponse().getContentAsString());
    assertEquals(0, userLoader.calls());
  }

  @Test
  void notModifiedResponseIsNotWrapped() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/not-modified").with(requestContext("req-not-modified")))
            .andExpect(status().isNotModified())
            .andReturn();

    assertEquals("", result.getResponse().getContentAsString());
    assertEquals(0, userLoader.calls());
  }

  @Test
  void actuatorEndpointsAreNotWrapped() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/actuator/health").with(requestContext("req-actuator")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode json = responseJson(result);

    assertEquals("UP", json.get("status").textValue());
    assertFalse(json.has("success"));
    assertFalse(json.has("resources"));
    assertEquals(0, userLoader.calls());
  }

  @Test
  void resourceLoadingFailureFallsBackToEmptyResources() throws Exception {
    ResourceLoader<Map<String, Object>> firstLoader = new CountingUserLoader();
    ResourceLoader<Map<String, Object>> duplicateLoader = new CountingUserLoader();
    MockMvc duplicateLoaderMockMvc =
        MockMvcBuilders.standaloneSetup(new ContractController())
            .setControllerAdvice(
                new ResourceEnvelopeAdvice(
                    new ResourceScanner(),
                    new ResourceLoadingService(List.of(firstLoader, duplicateLoader), List.of())),
                new GlobalExceptionHandler())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();

    MvcResult result =
        duplicateLoaderMockMvc
            .perform(
                get("/api/explicit-users/user-duplicate-loader")
                    .with(requestContext("req-duplicate-loader")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode json = responseJson(result);

    assertTrue(json.get("success").booleanValue());
    assertEquals("user-duplicate-loader", json.get("data").get("id").textValue());
    assertTrue(json.get("resources").isObject());
    assertEquals(0, json.get("resources").size());
  }

  @Test
  void controllerCollectedDynamicResourcesAreIncludedInMvcEnvelope() throws Exception {
    ResourceCollectorHolder collectorHolder = new ResourceCollectorHolder();
    MockMvc dynamicMockMvc =
        MockMvcBuilders.standaloneSetup(new DynamicContractController(collectorHolder))
            .setControllerAdvice(
                new ResourceEnvelopeAdvice(
                    new ResourceScanner(),
                    new ResourceLoadingService(List.of(userLoader), List.of()),
                    collectorHolder),
                new GlobalExceptionHandler())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();

    MvcResult result =
        dynamicMockMvc
            .perform(
                get("/api/dynamic-resources/dynamic-owner").with(requestContext("req-dynamic-mvc")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode json = responseJson(result);

    assertEquals("dynamic-owner", json.get("data").get("id").textValue());
    assertEquals(
        "User dynamic-owner",
        json.get("resources").get("users").get("dynamic-owner").get("displayName").textValue());
    assertEquals(
        "archive",
        json.get("resources")
            .get("options")
            .get("dynamic-actions")
            .get(0)
            .get("value")
            .textValue());
    assertEquals("req-dynamic-mvc", json.get("requestId").textValue());
  }

  @Test
  void successEnvelopeAlwaysContainsResources() throws Exception {
    ApiResponse<Map<String, String>> response =
        ApiResponse.ok(Map.of("id", "user-1"), ApiResources.empty(), "req-1", TIMESTAMP);

    JsonNode json = toJson(response);

    assertTrue(json.get("success").booleanValue());
    assertEquals("OK", json.get("code").textValue());
    assertEquals("OK", json.get("message").textValue());
    assertEquals("user-1", json.get("data").get("id").textValue());
    assertTrue(json.has("resources"));
    assertTrue(json.get("resources").isObject());
    assertEquals(0, json.get("resources").size());
    assertEquals("req-1", json.get("requestId").textValue());
    assertEquals("2026-04-25T10:00:00Z", json.get("timestamp").textValue());
  }

  @Test
  void compatibilitySuccessEnvelopeAlsoContainsEmptyResources() throws Exception {
    ApiResponse<Map<String, String>> response =
        ApiResponse.success(Map.of("id", "user-1"), "req-compat");

    JsonNode json = toJson(response);

    assertTrue(json.has("resources"));
    assertTrue(json.get("resources").isObject());
    assertEquals(0, json.get("resources").size());
    assertEquals("req-compat", json.get("requestId").textValue());
  }

  @Test
  void legacySuccessConstructorAlsoContainsEmptyResources() throws Exception {
    ApiResponse<Map<String, String>> response =
        new ApiResponse<>(true, "OK", "success", Map.of("id", "user-1"), "req-legacy", TIMESTAMP);

    JsonNode json = toJson(response);

    assertTrue(json.get("success").booleanValue());
    assertEquals("OK", json.get("code").textValue());
    assertEquals("success", json.get("message").textValue());
    assertEquals("user-1", json.get("data").get("id").textValue());
    assertTrue(json.has("resources"));
    assertTrue(json.get("resources").isObject());
    assertEquals(0, json.get("resources").size());
    assertEquals("req-legacy", json.get("requestId").textValue());
  }

  @Test
  void successEnvelopeTakesResourceSnapshot() throws Exception {
    ApiResources resources = ApiResources.empty();
    resources.put("users", Map.of("user-1", Map.of("displayName", "Alice")));

    ApiResponse<Map<String, String>> response =
        ApiResponse.ok(Map.of("id", "user-1"), resources, "req-snapshot", TIMESTAMP);
    resources.put("roles", Map.of("role-1", Map.of("displayName", "Admin")));

    JsonNode json = toJson(response);

    assertTrue(json.get("resources").has("users"));
    assertFalse(json.get("resources").has("roles"));
  }

  @Test
  void successEnvelopeTakesNestedResourceSnapshot() throws Exception {
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("displayName", "Alice");
    user.put("avatarUrl", null);
    user.put("tags", new ArrayList<>(List.of("owner")));
    Map<String, Object> users = new LinkedHashMap<>();
    users.put("user-1", user);
    ApiResources resources = ApiResources.empty();
    resources.put("users", users);

    ApiResponse<Map<String, String>> response =
        ApiResponse.ok(Map.of("id", "user-1"), resources, "req-nested-snapshot", TIMESTAMP);
    user.put("displayName", "Bob");
    users.put("user-2", Map.of("displayName", "Mallory"));
    ((List<String>) user.get("tags")).add("mutated");

    JsonNode json = toJson(response);

    assertEquals(
        "Alice", json.get("resources").get("users").get("user-1").get("displayName").textValue());
    assertTrue(json.get("resources").get("users").get("user-1").get("avatarUrl").isNull());
    assertEquals(1, json.get("resources").get("users").get("user-1").get("tags").size());
    assertFalse(json.get("resources").get("users").has("user-2"));
  }

  @Test
  void responseResourcesExposeImmutableReadViews() {
    ApiResources resources = ApiResources.empty();
    resources.put("users", Map.of("user-1", Map.of("displayName", "Alice")));
    ApiResponse<Map<String, String>> response =
        ApiResponse.ok(Map.of("id", "user-1"), resources, "req-immutable", TIMESTAMP);

    assertThrows(
        UnsupportedOperationException.class, () -> response.resources().asMap().remove("users"));
    assertThrows(UnsupportedOperationException.class, () -> response.resources().values().clear());
    assertThrows(
        UnsupportedOperationException.class,
        () -> response.resources().entrySet().iterator().next().setValue(Map.of()));
  }

  @Test
  void emptyResourcesRemainMutableForAssembly() throws Exception {
    ApiResources resources = ApiResources.empty();

    resources.put("users", Map.of("user-1", Map.of("displayName", "Alice")));
    resources.putAll(Map.of("roles", Map.of("role-1", Map.of("displayName", "Admin"))));

    JsonNode json = toJson(resources);

    assertEquals("Alice", json.get("users").get("user-1").get("displayName").textValue());
    assertEquals("Admin", json.get("roles").get("role-1").get("displayName").textValue());
  }

  @Test
  void errorEnvelopeContainsNullDataDetailsAndResources() throws Exception {
    ApiErrorResponse response =
        ApiErrorResponse.of(
            "VALIDATION_FAILED", "invalid request", Map.of("field", "email"), "req-2", TIMESTAMP);

    JsonNode json = toJson(response);

    assertFalse(json.get("success").booleanValue());
    assertEquals("VALIDATION_FAILED", json.get("code").textValue());
    assertEquals("invalid request", json.get("message").textValue());
    assertTrue(json.get("data").isNull());
    assertEquals("email", json.get("details").get("field").textValue());
    assertTrue(json.has("resources"));
    assertTrue(json.get("resources").isObject());
    assertEquals(0, json.get("resources").size());
    assertEquals("req-2", json.get("requestId").textValue());
    assertEquals("2026-04-25T10:00:00Z", json.get("timestamp").textValue());
  }

  @Test
  void compatibilityFailureEnvelopeAlsoContainsNullDataAndEmptyResources() throws Exception {
    ApiErrorResponse response =
        ApiErrorResponse.failure("VALIDATION_FAILED", "invalid request", "req-compat", null);

    JsonNode json = toJson(response);

    assertTrue(json.get("data").isNull());
    assertTrue(json.has("resources"));
    assertEquals(0, json.get("resources").size());
    assertTrue(json.get("details").isObject());
    assertEquals(0, json.get("details").size());
  }

  @Test
  void legacyErrorConstructorAlsoContainsNullDataAndEmptyResources() throws Exception {
    ApiErrorResponse response =
        new ApiErrorResponse(
            false,
            "VALIDATION_FAILED",
            "invalid request",
            "req-legacy-error",
            TIMESTAMP,
            Map.of("field", "email"));

    JsonNode json = toJson(response);

    assertFalse(json.get("success").booleanValue());
    assertEquals("VALIDATION_FAILED", json.get("code").textValue());
    assertEquals("invalid request", json.get("message").textValue());
    assertTrue(json.get("data").isNull());
    assertEquals("email", json.get("details").get("field").textValue());
    assertTrue(json.has("resources"));
    assertEquals(0, json.get("resources").size());
    assertEquals("req-legacy-error", json.get("requestId").textValue());
  }

  @Test
  void emptyApiResourcesSerializesAsEmptyObject() throws Exception {
    JsonNode json = toJson(ApiResources.empty());

    assertTrue(json.isObject());
    assertEquals(0, json.size());
  }

  @Test
  void pageResponseUsesOneBasedPageAndSortArray() throws Exception {
    PageResponse<String> response =
        new PageResponse<>(List.of("alpha", "beta"), 1, 20, 41, 3, null);

    JsonNode json = toJson(response);

    assertEquals("alpha", json.get("items").get(0).textValue());
    assertEquals(1, json.get("page").intValue());
    assertEquals(20, json.get("size").intValue());
    assertEquals(41, json.get("totalElements").longValue());
    assertEquals(3, json.get("totalPages").intValue());
    assertTrue(json.has("sort"));
    assertTrue(json.get("sort").isArray());
    assertEquals(0, json.get("sort").size());
  }

  @Test
  void pageResponseRejectsZeroBasedPage() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new PageResponse<>(List.of("alpha"), 0, 20, 1, 1, List.of()));

    assertNotNull(exception.getMessage());
  }

  private JsonNode toJson(Object value) throws Exception {
    return objectMapper.readTree(objectMapper.writeValueAsString(value));
  }

  private JsonNode responseJson(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private static RequestPostProcessor requestContext(String requestId) {
    return request -> {
      request.setAttribute(RequestContext.ATTRIBUTE_NAME, new RequestContext(requestId, requestId));
      return request;
    };
  }

  @RestController
  private static final class ContractController {

    @GetMapping("/api/users/{id}")
    UserView user(@PathVariable("id") String id) {
      return new UserView(id, id);
    }

    @GetMapping("/api/explicit-users/{id}")
    ApiResponse<UserView> explicitUser(@PathVariable("id") String id) {
      return ApiResponse.success(new UserView(id, id));
    }

    @GetMapping("/api/explicit-user-page")
    ApiResponse<PageResponse<UserView>> explicitUserPage() {
      return ApiResponse.success(
          new PageResponse<>(List.of(new UserView("page-user", "page-user")), 1, 20, 1, 1));
    }

    @GetMapping("/api/not-found")
    ResponseEntity<Map<String, String>> notFound() {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "USER_NOT_FOUND"));
    }

    @GetMapping("/api/problem")
    ProblemDetail problem() {
      return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
    }

    @GetMapping("/api/no-content")
    ResponseEntity<Void> noContent() {
      return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/not-modified")
    ResponseEntity<Void> notModified() {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
    }

    @GetMapping("/.well-known/openid-configuration")
    Map<String, String> openidConfiguration() {
      return Map.of("issuer", "https://issuer.example");
    }

    @GetMapping("/oauth2/authorize")
    Map<String, String> authorize() {
      return Map.of("authorization_endpoint", "/oauth2/authorize");
    }

    @GetMapping("/login/oauth2/code/{registrationId}")
    Map<String, String> oauth2Login(@PathVariable("registrationId") String registrationId) {
      return Map.of("registrationId", registrationId);
    }

    @GetMapping("/actuator/health")
    Map<String, String> health() {
      return Map.of("status", "UP");
    }

    @GetMapping("/api/error")
    UserView error() {
      throw new IllegalStateException("boom");
    }
  }

  @RestController
  private static final class DynamicContractController {

    private final ResourceCollectorHolder collectorHolder;

    private DynamicContractController(ResourceCollectorHolder collectorHolder) {
      this.collectorHolder = collectorHolder;
    }

    @GetMapping("/api/dynamic-resources/{id}")
    ApiResponse<Map<String, String>> dynamic(@PathVariable("id") String id) {
      collectorHolder
          .current()
          .ref(CountingUserLoader.class, id)
          .options("dynamic-actions", List.of(ResourceOptionItem.enabled("archive", "Archive")));
      return ApiResponse.success(Map.of("id", id));
    }
  }

  private record UserView(String id, @ResourceRef(CountingUserLoader.class) String ownerId) {}

  private static final class CountingUserLoader implements ResourceLoader<Map<String, Object>> {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public String resourceType() {
      return "users";
    }

    @Override
    public Map<String, Map<String, Object>> load(
        String domain, String projection, Set<String> keys, ResourceContext context) {
      calls.incrementAndGet();
      Map<String, Map<String, Object>> users = new LinkedHashMap<>();
      keys.forEach(key -> users.put(key, Map.of("id", key, "displayName", "User " + key)));
      return users;
    }

    int calls() {
      return calls.get();
    }
  }
}
