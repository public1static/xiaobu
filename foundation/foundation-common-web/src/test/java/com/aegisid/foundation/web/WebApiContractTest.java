package com.aegisid.foundation.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.aegisid.foundation.shared.page.PageResult;
import com.aegisid.foundation.shared.page.SortOrder;
import com.aegisid.foundation.shared.request.RequestContextView;
import com.aegisid.foundation.web.api.ApiErrorResponse;
import com.aegisid.foundation.web.api.ApiResponse;
import com.aegisid.foundation.web.api.PageResponse;
import com.aegisid.foundation.web.error.GlobalExceptionHandler;
import com.aegisid.foundation.web.page.PageRequestParams;
import com.aegisid.foundation.web.request.RequestContext;
import com.aegisid.foundation.web.request.RequestIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class WebApiContractTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Test
  void successResponseContainsStableEnvelopeFields() throws Exception {
    ApiResponse<Map<String, String>> response =
        ApiResponse.success(Map.of("id", "user-1"), "req-1");

    JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

    assertTrue(json.get("success").booleanValue());
    assertEquals("OK", json.get("code").textValue());
    assertEquals("success", json.get("message").textValue());
    assertEquals("user-1", json.get("data").get("id").textValue());
    assertEquals("req-1", json.get("requestId").textValue());
    assertNotNull(json.get("timestamp").textValue());
  }

  @Test
  void successResponseCanResolveRequestIdFromCurrentRequestContext() {
    RequestContext.setCurrent(new RequestContext("req-current", "trace-current"));
    try {
      ApiResponse<Map<String, String>> response = ApiResponse.success(Map.of("id", "user-1"));

      assertEquals("req-current", response.requestId());
    } finally {
      RequestContext.clear();
    }
  }

  @Test
  void emptySuccessResponseUsesCurrentRequestContextAndNullData() {
    RequestContext.setCurrent(new RequestContext("req-empty", "trace-empty"));
    try {
      ApiResponse<Void> response = ApiResponse.empty();

      assertTrue(response.success());
      assertNull(response.data());
      assertEquals("req-empty", response.requestId());
    } finally {
      RequestContext.clear();
    }
  }

  @Test
  void errorResponseContainsStableEnvelopeFieldsAndDetails() {
    ApiErrorResponse response =
        ApiErrorResponse.failure(
            "IAM-TEST-0001", "invalid request", "req-2", Map.of("field", "email"));

    assertFalse(response.success());
    assertEquals("IAM-TEST-0001", response.code());
    assertEquals("invalid request", response.message());
    assertEquals("req-2", response.requestId());
    assertNotNull(response.timestamp());
    assertEquals(Map.of("field", "email"), response.details());
  }

  @Test
  void errorResponseTreatsNullDetailsAsEmptyMap() {
    ApiErrorResponse response =
        ApiErrorResponse.failure("IAM-TEST-0001", "invalid request", "req-2", null);

    assertTrue(response.details().isEmpty());
  }

  @Test
  void errorResponseCanonicalConstructorForcesSuccessFalse() {
    ApiErrorResponse response =
        new ApiErrorResponse(
            true, "IAM-TEST-0001", "invalid request", "payload", null, null, "req-canonical", null);

    assertFalse(response.success());
    assertNull(response.data());
    assertTrue(response.details().isEmpty());
    assertTrue(response.resources().asMap().isEmpty());
  }

  @Test
  void pageResponseExposesOneBasedPageNumber() {
    PageResult<String> result = new PageResult<>(List.of("alpha", "beta"), 2, 20, 41);

    PageResponse<String> response = PageResponse.from(result);

    assertEquals(List.of("alpha", "beta"), response.items());
    assertEquals(2, response.page());
    assertEquals(20, response.size());
    assertEquals(41, response.totalElements());
    assertEquals(3, response.totalPages());
  }

  @Test
  void pageResponseMapsSharedSortOrdersToExternalSortArray() {
    PageResult<String> result =
        new PageResult<>(List.of("alpha"), 1, 20, 1, 1, List.of(SortOrder.desc("createdAt")));

    PageResponse<String> response = PageResponse.from(result);

    assertEquals(List.of(new PageResponse.Sort("createdAt", "DESC")), response.sort());
  }

  @Test
  void pageRequestParamsApplyDefaultsForMissingQueryParameters() {
    PageRequestParams params = new PageRequestParams(null, null, null);

    assertEquals(1, params.toPageQuery().page());
    assertEquals(20, params.toPageQuery().size());
    assertTrue(params.toPageQuery().sort().isEmpty());
  }

  @Test
  void pageRequestParamsParseFlattenedSortQueryParameters() {
    PageRequestParams params = new PageRequestParams(2, 50, List.of("createdAt,DESC", "name,ASC"));

    assertEquals(2, params.toPageQuery().page());
    assertEquals(50, params.toPageQuery().size());
    assertEquals(
        List.of(SortOrder.desc("createdAt"), SortOrder.asc("name")), params.toPageQuery().sort());
  }

  @Test
  void requestIdFilterPreservesIncomingRequestId() throws Exception {
    RequestIdFilter filter = new RequestIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "req-incoming");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals("req-incoming", response.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
    assertInstanceOf(RequestContextView.class, request.getAttribute(RequestContext.ATTRIBUTE_NAME));
  }

  @Test
  void requestIdFilterIsRegisteredAsSpringBeanWhenScanned() {
    Component component = AnnotationUtils.findAnnotation(RequestIdFilter.class, Component.class);

    assertNotNull(component);
  }

  @Test
  void requestIdFilterGeneratesRequestIdWhenHeaderMissing() throws Exception {
    RequestIdFilter filter = new RequestIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    String generatedRequestId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
    assertNotNull(generatedRequestId);
    assertFalse(generatedRequestId.isBlank());
    assertEquals(
        generatedRequestId,
        ((RequestContextView) request.getAttribute(RequestContext.ATTRIBUTE_NAME)).requestId());
  }

  @Test
  void requestIdFilterReplacesTooLongRequestIdBeforeEchoing() throws Exception {
    RequestIdFilter filter = new RequestIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(
        RequestIdFilter.REQUEST_ID_HEADER,
        "a".repeat(RequestIdFilter.MAX_CORRELATION_ID_LENGTH + 1));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    String requestId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
    assertNotNull(requestId);
    assertFalse(requestId.startsWith("a".repeat(10)));
    assertEquals(
        requestId,
        ((RequestContextView) request.getAttribute(RequestContext.ATTRIBUTE_NAME)).requestId());
  }

  @Test
  void requestIdFilterReplacesControlCharacterRequestIdBeforeEchoing() throws Exception {
    RequestIdFilter filter = new RequestIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "req\nbad");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertFalse(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER).contains("\n"));
  }

  @Test
  void requestIdFilterReplacesInvalidTraceIdBeforeEchoing() throws Exception {
    RequestIdFilter filter = new RequestIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "req-valid");
    request.addHeader(RequestIdFilter.TRACE_ID_HEADER, "trace invalid");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    RequestContextView context =
        (RequestContextView) request.getAttribute(RequestContext.ATTRIBUTE_NAME);
    assertEquals("req-valid", context.requestId());
    assertFalse("trace invalid".equals(response.getHeader(RequestIdFilter.TRACE_ID_HEADER)));
    assertEquals(response.getHeader(RequestIdFilter.TRACE_ID_HEADER), context.traceId());
  }

  @Test
  void requestIdFilterSetsRequestContextDuringFilterChainAndClearsThreadLocalAfterwards()
      throws Exception {
    RequestIdFilter filter = new RequestIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "req-observable");
    request.addHeader(RequestIdFilter.TRACE_ID_HEADER, "trace-observable");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (servletRequest, servletResponse) -> {
          RequestContextView context = RequestContext.current();
          assertEquals("req-observable", context.requestId());
          assertEquals("trace-observable", context.traceId());
          assertSameContext(
              context,
              (RequestContextView) servletRequest.getAttribute(RequestContext.ATTRIBUTE_NAME));
        });

    assertNull(RequestContext.currentOrNull());
  }

  @Test
  void globalExceptionHandlerUsesSafeFallbackAndExplicitBusinessDetails() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(
        RequestContext.ATTRIBUTE_NAME, new RequestContext("req-handler", "trace-handler"));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      GlobalExceptionHandler handler = new GlobalExceptionHandler();
      FoundationException exception =
          new FoundationException(
              new ErrorCode("IAM-TEST-0002"),
              ErrorCategory.NOT_FOUND,
              "business message: {hidden}",
              Map.of("resourceType", "tenant"));

      ResponseEntity<ApiErrorResponse> response = handler.handleFoundationException(exception);

      assertEquals("IAM-TEST-0002", response.getBody().code());
      assertEquals("Request could not be processed", response.getBody().message());
      assertEquals("req-handler", response.getBody().requestId());
      assertEquals(Map.of("resourceType", "tenant"), response.getBody().details());
      assertEquals(404, response.getStatusCode().value());
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
  }

  @Test
  void globalExceptionHandlerLocalizesErrorMessageByErrorCodeAndLocale() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(
        RequestContext.ATTRIBUTE_NAME, new RequestContext("req-i18n", "trace-i18n"));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
    try {
      StaticMessageSource messageSource = new StaticMessageSource();
      messageSource.addMessage("error.IAM-TEST-0003", Locale.SIMPLIFIED_CHINESE, "请求参数不正确");
      GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource);
      FoundationException exception =
          new FoundationException(new ErrorCode("IAM-TEST-0003"), "IAM-TEST-0003");

      ResponseEntity<ApiErrorResponse> response = handler.handleFoundationException(exception);

      assertEquals("IAM-TEST-0003", response.getBody().code());
      assertEquals("请求参数不正确", response.getBody().message());
      assertEquals("req-i18n", response.getBody().requestId());
    } finally {
      LocaleContextHolder.resetLocaleContext();
      RequestContextHolder.resetRequestAttributes();
    }
  }

  @Test
  void globalExceptionHandlerMapsTransportNeutralErrorCategoriesToHttpStatuses() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    Map<ErrorCategory, Integer> expectedStatuses =
        Map.ofEntries(
            Map.entry(ErrorCategory.INVALID_ARGUMENT, 400),
            Map.entry(ErrorCategory.UNAUTHENTICATED, 401),
            Map.entry(ErrorCategory.FORBIDDEN, 403),
            Map.entry(ErrorCategory.NOT_FOUND, 404),
            Map.entry(ErrorCategory.CONFLICT, 409),
            Map.entry(ErrorCategory.RULE_VIOLATION, 422),
            Map.entry(ErrorCategory.RATE_LIMITED, 429),
            Map.entry(ErrorCategory.UPSTREAM_FAILURE, 502),
            Map.entry(ErrorCategory.SERVICE_UNAVAILABLE, 503),
            Map.entry(ErrorCategory.INTERNAL, 500));

    expectedStatuses.forEach(
        (category, status) -> {
          FoundationException exception =
              new FoundationException(
                  new ErrorCode("IAM-TEST-" + category.name()), category, "hidden", Map.of());

          assertEquals(
              status, handler.handleFoundationException(exception).getStatusCode().value());
        });
  }

  private static void assertSameContext(RequestContextView expected, RequestContextView actual) {
    assertEquals(expected.requestId(), actual.requestId());
    assertEquals(expected.traceId(), actual.traceId());
  }
}
