package com.aegisid.foundation.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aegisid.foundation.shared.request.RequestContextView;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;

class ObservabilityTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void mdcKeysExposeStableCorrelationAndTenantKeys() {
    assertEquals("requestId", MdcKeys.REQUEST_ID);
    assertEquals("traceId", MdcKeys.TRACE_ID);
    assertEquals("actorId", MdcKeys.ACTOR_ID);
    assertEquals("tenantId", MdcKeys.TENANT_ID);
  }

  @Test
  void contributorWritesRequestAndTraceIdsIntoMdc() {
    RequestContextMdcContributor contributor = new RequestContextMdcContributor();

    contributor.contribute(new TestRequestContext("req-123", "trace-456"));

    assertEquals("req-123", MDC.get(MdcKeys.REQUEST_ID));
    assertEquals("trace-456", MDC.get(MdcKeys.TRACE_ID));
  }

  @Test
  void bridgeReadsRequestAndTraceIdsFromRequestContextView() {
    RequestContextMdcBridge bridge = new RequestContextMdcBridge();

    Map<String, String> values = bridge.toMdcValues(new TestRequestContext("req-abc", "trace-def"));

    assertEquals("req-abc", values.get(MdcKeys.REQUEST_ID));
    assertEquals("trace-def", values.get(MdcKeys.TRACE_ID));
  }

  @Test
  void contributorClearsOnlyFoundationMdcKeysAfterRequestEnds() {
    RequestContextMdcContributor contributor = new RequestContextMdcContributor();
    contributor.contribute(new TestRequestContext("req-clear", "trace-clear"));
    MDC.put(MdcKeys.ACTOR_ID, "actor-1");
    MDC.put(MdcKeys.TENANT_ID, "tenant-1");
    MDC.put("external", "keep-me");

    contributor.clear();

    assertNull(MDC.get(MdcKeys.REQUEST_ID));
    assertNull(MDC.get(MdcKeys.TRACE_ID));
    assertNull(MDC.get(MdcKeys.ACTOR_ID));
    assertNull(MDC.get(MdcKeys.TENANT_ID));
    assertEquals("keep-me", MDC.get("external"));
  }

  @Test
  void versionInfoExposesApplicationVersion() {
    VersionInfo versionInfo = new VersionInfo("aegisid", "1.2.3", "2026-04-25T00:00:00Z");

    assertEquals("1.2.3", versionInfo.applicationVersion());
    assertFalse(VersionInfo.defaults().applicationVersion().isBlank());
  }

  @Test
  void requestLoggingRedactsSensitiveQueryParameters() {
    assertTrue(
        RequestLoggingFilter.redactQueryString("password=open&name=ada")
            .contains("password=<redacted>"));
    assertTrue(
        RequestLoggingFilter.redactQueryString("refresh_token=rt&client_id=client")
            .contains("refresh_token=<redacted>"));
    assertTrue(
        RequestLoggingFilter.redactQueryString(
                "access_token=at&id_token=id&reset_token=reset&token=generic")
            .contains(
                "access_token=<redacted>&id_token=<redacted>&reset_token=<redacted>&token=<redacted>"));
    assertFalse(
        RequestLoggingFilter.redactQueryString("invitationToken=invite-1").contains("invite-1"));
    assertFalse(RequestLoggingFilter.redactQueryString("secret=value").contains("value"));
  }

  @Test
  void requestLoggingRedactsEncodedSensitiveQueryParameterNames() {
    String sanitized =
        RequestLoggingFilter.redactQueryString(
            "refresh%5Ftoken=refresh-secret&authorization%5Fcode=auth-secret&invitation%2Dtoken=invite-secret");

    assertTrue(sanitized.contains("refresh%5Ftoken=<redacted>"));
    assertTrue(sanitized.contains("authorization%5Fcode=<redacted>"));
    assertTrue(sanitized.contains("invitation%2Dtoken=<redacted>"));
    assertFalse(sanitized.contains("refresh-secret"));
    assertFalse(sanitized.contains("auth-secret"));
    assertFalse(sanitized.contains("invite-secret"));
  }

  @Test
  void requestLoggingRedactsEncodedTokenQueryParameterNames() {
    String sanitized =
        RequestLoggingFilter.redactQueryString(
            "access%5Ftoken=access-secret&id%5Ftoken=id-secret&reset%5Ftoken=reset-secret&to%6Ben=generic-secret");

    assertTrue(sanitized.contains("access%5Ftoken=<redacted>"));
    assertTrue(sanitized.contains("id%5Ftoken=<redacted>"));
    assertTrue(sanitized.contains("reset%5Ftoken=<redacted>"));
    assertTrue(sanitized.contains("to%6Ben=<redacted>"));
    assertFalse(sanitized.contains("access-secret"));
    assertFalse(sanitized.contains("id-secret"));
    assertFalse(sanitized.contains("reset-secret"));
    assertFalse(sanitized.contains("generic-secret"));
  }

  @Test
  void requestLoggingRedactsMalformedSuspiciousQueryParameterNames() {
    String sanitized =
        RequestLoggingFilter.redactQueryString("refresh%5token=refresh-secret&safe=value");

    assertTrue(sanitized.contains("refresh%5token=<redacted>"));
    assertTrue(sanitized.contains("safe=value"));
    assertFalse(sanitized.contains("refresh-secret"));
  }

  @Test
  void requestLoggingRedactsFederationAndCasQuerySecrets() {
    String sanitized =
        RequestLoggingFilter.redactQueryString(
            "SAMLResponse=saml-response&SAMLRequest=saml-request&code_verifier=pkce-secret&ticket=cas-ticket");

    assertTrue(sanitized.contains("SAMLResponse=<redacted>"));
    assertTrue(sanitized.contains("SAMLRequest=<redacted>"));
    assertTrue(sanitized.contains("code_verifier=<redacted>"));
    assertTrue(sanitized.contains("ticket=<redacted>"));
    assertFalse(sanitized.contains("saml-response"));
    assertFalse(sanitized.contains("saml-request"));
    assertFalse(sanitized.contains("pkce-secret"));
    assertFalse(sanitized.contains("cas-ticket"));
  }

  @Test
  void requestLoggingRedactsOpaqueProtocolStateParameters() {
    String sanitized =
        RequestLoggingFilter.redactQueryString(
            "state=oauth-state&nonce=oidc-nonce&RelayState=saml-relay&session_state=op-session");

    assertTrue(sanitized.contains("state=<redacted>"));
    assertTrue(sanitized.contains("nonce=<redacted>"));
    assertTrue(sanitized.contains("RelayState=<redacted>"));
    assertTrue(sanitized.contains("session_state=<redacted>"));
    assertFalse(sanitized.contains("oauth-state"));
    assertFalse(sanitized.contains("oidc-nonce"));
    assertFalse(sanitized.contains("saml-relay"));
    assertFalse(sanitized.contains("op-session"));
  }

  @Test
  void requestLoggingFilterContributesMdcDuringDownstreamProcessingAndClearsAfterward()
      throws ServletException, IOException {
    RequestLoggingFilter filter = new RequestLoggingFilter(new RequestContextMdcContributor());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
    request.setAttribute(
        RequestLoggingFilter.REQUEST_CONTEXT_ATTRIBUTE_NAME,
        new TestRequestContext("req-filter", "trace-filter"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (servletRequest, servletResponse) -> {
          assertEquals("req-filter", MDC.get(MdcKeys.REQUEST_ID));
          assertEquals("trace-filter", MDC.get(MdcKeys.TRACE_ID));
        });

    assertNull(MDC.get(MdcKeys.REQUEST_ID));
    assertNull(MDC.get(MdcKeys.TRACE_ID));
  }

  @Test
  void requestLoggingFilterAddsActorContextToMdcBeforeWritingAccessLog()
      throws ServletException, IOException {
    RequestLoggingFilter filter = new RequestLoggingFilter(new RequestContextMdcContributor());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/console/profile");
    request.setAttribute(
        RequestLoggingFilter.REQUEST_CONTEXT_ATTRIBUTE_NAME,
        new TestRequestContext("req-actor", "trace-actor"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    logger.addAppender(appender);

    try {
      filter.doFilter(
          request,
          response,
          (servletRequest, servletResponse) -> {
            servletRequest.setAttribute(
                RequestLoggingFilter.ACTOR_CONTEXT_ATTRIBUTE_NAME,
                new TestActorContext("user-console", "tenant-acme"));
          });
    } finally {
      logger.detachAppender(appender);
    }

    ILoggingEvent accessLog = appender.list.getLast();
    assertEquals("user-console", accessLog.getMDCPropertyMap().get(MdcKeys.ACTOR_ID));
    assertEquals("tenant-acme", accessLog.getMDCPropertyMap().get(MdcKeys.TENANT_ID));
    assertNull(MDC.get(MdcKeys.ACTOR_ID));
    assertNull(MDC.get(MdcKeys.TENANT_ID));
  }

  @Test
  void observabilityComponentsAreRegisteredAsSpringBeansWhenScanned() {
    assertNotNull(
        AnnotationUtils.findAnnotation(RequestContextMdcContributor.class, Component.class));
    assertNotNull(AnnotationUtils.findAnnotation(RequestLoggingFilter.class, Component.class));
  }

  @Test
  void observabilityAutoConfigurationIsRegisteredForSpringBootDiscovery() throws IOException {
    String importLocation =
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(importLocation)) {
      assertNotNull(stream);

      String imports = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(imports.contains(ObservabilityAutoConfiguration.class.getName()));
    }
  }

  private record TestRequestContext(String requestId, String traceId)
      implements RequestContextView {}

  private record TestActorContext(String actorId, String tenantId) {}
}
