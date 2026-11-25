package com.aegisid.foundation.security.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisid.foundation.security.actor.ActorContext;
import com.aegisid.foundation.security.actor.ActorType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ActorTypeAuthorizationFilterTest {

  @Test
  void allowsConsoleActorToAccessConsoleApi() throws Exception {
    ActorTypeAuthorizationFilter filter =
        new ActorTypeAuthorizationFilter(new HeaderActorContextResolver());
    MockHttpServletRequest request = request("/api/v1/console/user-pools", ActorType.CONSOLE_USER);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertEquals(request, chain.getRequest());
  }

  @Test
  void allowsConsoleActorToAccessAccountCenterApi() throws Exception {
    ActorTypeAuthorizationFilter filter =
        new ActorTypeAuthorizationFilter(new HeaderActorContextResolver());
    MockHttpServletRequest request =
        request("/api/v1/account-center/context", ActorType.CONSOLE_USER);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertEquals(request, chain.getRequest());
  }

  @Test
  void allowsDeveloperActorToAccessDeveloperApi() throws Exception {
    ActorTypeAuthorizationFilter filter =
        new ActorTypeAuthorizationFilter(new HeaderActorContextResolver());
    MockHttpServletRequest request = request("/api/v1/developer/profile", ActorType.DEVELOPER);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertEquals(request, chain.getRequest());
  }

  @Test
  void allowsInternalAdminActorToAccessInternalAdminApi() throws Exception {
    ActorTypeAuthorizationFilter filter =
        new ActorTypeAuthorizationFilter(new HeaderActorContextResolver());
    MockHttpServletRequest request =
        request("/api/v1/internal-admin/tenants", ActorType.INTERNAL_ADMIN);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertEquals(request, chain.getRequest());
  }

  @Test
  void rejectsEndUserActorFromConsoleApi() throws Exception {
    ActorTypeAuthorizationFilter filter =
        new ActorTypeAuthorizationFilter(new HeaderActorContextResolver());
    MockHttpServletRequest request = request("/api/v1/console/user-pools", ActorType.END_USER);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("ACTOR_TYPE_FORBIDDEN"));
  }

  @Test
  void rejectsConsoleActorFromInternalAdminApi() throws Exception {
    ActorTypeAuthorizationFilter filter =
        new ActorTypeAuthorizationFilter(new HeaderActorContextResolver());
    MockHttpServletRequest request =
        request("/api/v1/internal-admin/tenants", ActorType.CONSOLE_USER);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("ACTOR_TYPE_FORBIDDEN"));
  }

  @Test
  void rejectsMissingActorFromProtectedApi() throws Exception {
    ActorTypeAuthorizationFilter filter =
        new ActorTypeAuthorizationFilter(new HeaderActorContextResolver());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/internal/tenants");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("ACTOR_CONTEXT_REQUIRED"));
  }

  @Test
  void allowsPublicConsoleInvitationAcceptWithoutActor() throws Exception {
    ActorTypeAuthorizationFilter filter =
        new ActorTypeAuthorizationFilter(new HeaderActorContextResolver());
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/v1/console/members/invitations/accept");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertEquals(request, chain.getRequest());
  }

  @Test
  void rejectsUnknownApiEntryByDefault() throws Exception {
    ActorTypeAuthorizationFilter filter =
        new ActorTypeAuthorizationFilter(new HeaderActorContextResolver());
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/v1/unclassified/probe");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("API_ENTRY_FORBIDDEN"));
  }

  @Test
  void allowsLoginApiPublicPrefix() throws Exception {
    ActorTypeAuthorizationFilter filter =
        new ActorTypeAuthorizationFilter(new HeaderActorContextResolver());
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/login/cancel");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertEquals(request, chain.getRequest());
  }

  @Test
  void ignoresOauthProtocolEndpoints() throws Exception {
    ActorTypeAuthorizationFilter filter =
        new ActorTypeAuthorizationFilter(new HeaderActorContextResolver());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/jwks");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertEquals(request, chain.getRequest());
  }

  @Test
  void jwtResolverBuildsActorContextFromJwtAuthentication() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("pool:user")
            .claim("actor_type", "console_user")
            .claim("user_id", "user-1")
            .claim("tenant_id", "tenant-1")
            .claim("user_pool_id", "pool-1")
            .claim("application_id", "app-1")
            .claim("client_id", "client-1")
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    try {
      Optional<ActorContext> actor =
          new JwtAuthenticationActorContextResolver().resolve(new MockHttpServletRequest());

      assertTrue(actor.isPresent());
      assertEquals(ActorType.CONSOLE_USER, actor.orElseThrow().actorType());
      assertEquals("user-1", actor.orElseThrow().userId());
      assertEquals("pool:user", actor.orElseThrow().subject());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void jwtResolverIgnoresNonJwtAuthentication() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "password"));
    try {
      assertTrue(
          new JwtAuthenticationActorContextResolver()
              .resolve(new MockHttpServletRequest())
              .isEmpty());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static MockHttpServletRequest request(String path, ActorType actorType) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.addHeader(HeaderActorContextResolver.ACTOR_TYPE_HEADER, actorType.claimValue());
    request.addHeader(HeaderActorContextResolver.USER_ID_HEADER, "user-1");
    request.addHeader(HeaderActorContextResolver.TENANT_ID_HEADER, "tenant-1");
    request.addHeader(HeaderActorContextResolver.USER_POOL_ID_HEADER, "pool-1");
    request.addHeader(HeaderActorContextResolver.APPLICATION_ID_HEADER, "app-1");
    request.addHeader(HeaderActorContextResolver.CLIENT_ID_HEADER, "client-1");
    return request;
  }
}
