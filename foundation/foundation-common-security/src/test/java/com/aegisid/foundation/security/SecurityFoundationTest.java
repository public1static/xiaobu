package com.aegisid.foundation.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisid.foundation.cache.CachePort;
import com.aegisid.foundation.cache.CaffeineCachePort;
import com.aegisid.foundation.security.actor.ActorContext;
import com.aegisid.foundation.security.actor.ActorType;
import com.aegisid.foundation.security.attempt.LoginAttemptLimiter;
import com.aegisid.foundation.security.password.PasswordService;
import com.aegisid.foundation.security.secret.SecretHashService;
import com.aegisid.foundation.security.web.JwtAuthenticationActorContextResolver;
import com.aegisid.foundation.security.web.SecurityHeadersConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.cors.CorsConfiguration;

class SecurityFoundationTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void passwordServiceHashesAndVerifiesPasswords() {
    PasswordService service = new PasswordService();

    String hash = service.hash("correct horse battery staple");

    assertNotEquals("correct horse battery staple", hash);
    assertTrue(service.matches("correct horse battery staple", hash));
    assertFalse(service.matches("wrong password", hash));
  }

  @Test
  void secretHashServiceHashesSecretsAndMasksToString() {
    SecretHashService service = new SecretHashService();

    SecretHashService.SecretHash hash = service.hash("refresh-token-secret");

    assertNotEquals("refresh-token-secret", hash.value());
    assertEquals(hash, service.hash("refresh-token-secret"));
    assertFalse(hash.toString().contains("refresh-token-secret"));
    assertTrue(hash.toString().contains("<redacted>"));
  }

  @Test
  void loginAttemptLimiterIncrementsFailureCountViaCachePort() {
    CachePort cache = new CaffeineCachePort();
    LoginAttemptLimiter limiter = new LoginAttemptLimiter(cache, 3, Duration.ofMinutes(5));

    limiter.recordFailure("ada@example.com");
    limiter.recordFailure("ada@example.com");

    assertEquals(2, limiter.failureCount("ada@example.com"));
    assertEquals(
        2, cache.get(LoginAttemptLimiter.cacheKey("ada@example.com"), Integer.class).orElseThrow());
    assertFalse(limiter.isLocked("ada@example.com"));
  }

  @Test
  void loginAttemptLimiterLocksAfterThresholdAndCanReset() {
    LoginAttemptLimiter limiter =
        new LoginAttemptLimiter(new CaffeineCachePort(), 2, Duration.ofMinutes(5));

    limiter.recordFailure("grace@example.com");
    assertFalse(limiter.isLocked("grace@example.com"));

    limiter.recordFailure("grace@example.com");
    assertTrue(limiter.isLocked("grace@example.com"));

    limiter.reset("grace@example.com");
    assertFalse(limiter.isLocked("grace@example.com"));
    assertEquals(0, limiter.failureCount("grace@example.com"));
  }

  @Test
  void loginAttemptLimiterIncrementsAtomicallyAcrossLimiterInstances() throws InterruptedException {
    CachePort cache = new CaffeineCachePort();
    LoginAttemptLimiter firstLimiter = new LoginAttemptLimiter(cache, 100, Duration.ofMinutes(5));
    LoginAttemptLimiter secondLimiter = new LoginAttemptLimiter(cache, 100, Duration.ofMinutes(5));
    int attempts = 40;
    ExecutorService executor = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);

    for (int index = 0; index < attempts; index++) {
      LoginAttemptLimiter limiter = index % 2 == 0 ? firstLimiter : secondLimiter;
      executor.submit(
          () -> {
            try {
              start.await();
              limiter.recordFailure("shared@example.com");
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
            }
          });
    }

    start.countDown();
    executor.shutdown();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

    assertEquals(attempts, firstLimiter.failureCount("shared@example.com"));
    assertEquals(attempts, secondLimiter.failureCount("shared@example.com"));
  }

  @Test
  void loginAttemptLimiterRejectsBlankSubjects() {
    LoginAttemptLimiter limiter =
        new LoginAttemptLimiter(new CaffeineCachePort(), 2, Duration.ofMinutes(5));

    assertThrows(IllegalArgumentException.class, () -> LoginAttemptLimiter.cacheKey(" "));
    assertThrows(IllegalArgumentException.class, () -> limiter.recordFailure(""));
    assertThrows(IllegalArgumentException.class, () -> limiter.failureCount(" "));
    assertThrows(IllegalArgumentException.class, () -> limiter.reset(""));
  }

  @Test
  void securityHeadersConfigDeclaresBaselineHeadersAndCors() {
    SecurityHeadersConfig config = SecurityHeadersConfig.defaults();

    assertTrue(config.hsts().includeSubDomains());
    assertTrue(config.hsts().maxAge().compareTo(Duration.ofDays(365)) >= 0);
    assertEquals(SecurityHeadersConfig.FrameOptions.DENY, config.frameOptions());
    assertTrue(config.contentTypeOptions());
    assertTrue(config.contentSecurityPolicy().contains("default-src 'self'"));
    assertEquals("no-referrer", config.referrerPolicy());
    assertTrue(config.permissionsPolicy().contains("camera=()"));

    CorsConfiguration cors = config.corsConfiguration();
    assertEquals(
        List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"), cors.getAllowedMethods());
    assertEquals(List.of("*"), cors.getAllowedOriginPatterns());
    assertNotNull(cors.getAllowedHeaders());
    assertTrue(cors.getAllowedHeaders().contains("Authorization"));
  }

  @Test
  void actorContextExposesCurrentActorWithoutBusinessDependencies() {
    ActorContext actor = new ActorContext("user-1", "tenant-1", List.of("ROLE_ADMIN"));

    assertEquals("user-1", actor.actorId());
    assertEquals("tenant-1", actor.tenantId());
    assertTrue(actor.authorities().contains("ROLE_ADMIN"));
  }

  @Test
  void actorContextCarriesEntryIsolationFieldsAndKeepsAuthoritiesImmutable() {
    List<String> authorities = new java.util.ArrayList<>(List.of("iam:user:read"));

    ActorContext actor =
        new ActorContext(
            ActorType.CONSOLE_USER,
            "user-1",
            "user-1",
            "pool-console:user-1",
            "tenant-1",
            "pool-console",
            "app-console",
            "aegisid-console",
            authorities);
    authorities.add("iam:user:write");

    assertEquals(ActorType.CONSOLE_USER, actor.actorType());
    assertEquals("user-1", actor.actorId());
    assertEquals("user-1", actor.userId());
    assertEquals("pool-console:user-1", actor.subject());
    assertEquals("tenant-1", actor.tenantId());
    assertEquals("pool-console", actor.userPoolId());
    assertEquals("app-console", actor.applicationId());
    assertEquals("aegisid-console", actor.clientId());
    assertEquals(List.of("iam:user:read"), actor.authorities());
    assertThrows(
        UnsupportedOperationException.class, () -> actor.authorities().add("iam:user:delete"));
  }

  @Test
  void actorContextLegacyConstructorUsesAnonymousActorTypeButKeepsExistingFields() {
    ActorContext actor = new ActorContext("user-1", "tenant-1", List.of("ROLE_ADMIN"));

    assertEquals(ActorType.ANONYMOUS, actor.actorType());
    assertEquals("user-1", actor.actorId());
    assertEquals("tenant-1", actor.tenantId());
    assertEquals(List.of("ROLE_ADMIN"), actor.authorities());
  }

  @Test
  void actorContextAnonymousIsNotAProtectedApiActor() {
    ActorContext actor = ActorContext.anonymous();

    assertEquals(ActorType.ANONYMOUS, actor.actorType());
    assertEquals("anonymous", actor.actorId());
    assertTrue(actor.authorities().isEmpty());
  }

  @Test
  void jwtAuthenticationActorContextResolverCarriesSelectedTenantClaims() {
    Instant issuedAt = Instant.parse("2026-05-21T00:00:00Z");
    Jwt jwt =
        new Jwt(
            "access-token",
            issuedAt,
            issuedAt.plus(Duration.ofMinutes(5)),
            Map.of("alg", "none"),
            Map.of(
                "sub", "pool-1:user-1",
                "actor_type", "end_user",
                "user_id", "user-1",
                "tenant_id", "tenant_customer_a",
                "parent_tenant_id", "tenant-1",
                "user_pool_id", "pool-1",
                "tenant_code", "customer-a",
                "tenant_name", "客户 A"));
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    ActorContext actor =
        new JwtAuthenticationActorContextResolver()
            .resolve(new MockHttpServletRequest())
            .orElseThrow();

    assertEquals("tenant_customer_a", actor.tenantId());
    assertEquals("tenant-1", actor.parentTenantId());
    assertEquals("tenant_customer_a", actor.customerTenantId());
    assertEquals("customer-a", actor.customerTenantCode());
    assertEquals("客户 A", actor.customerTenantName());
  }

  @Test
  void
      jwtAuthenticationActorContextResolverTreatsUserPoolTenantWithParentAsSelectedTenantWithoutCode() {
    Instant issuedAt = Instant.parse("2026-05-21T00:00:00Z");
    Jwt jwt =
        new Jwt(
            "access-token",
            issuedAt,
            issuedAt.plus(Duration.ofMinutes(5)),
            Map.of("alg", "none"),
            Map.of(
                "sub", "pool-1:user-1",
                "actor_type", "end_user",
                "user_id", "user-1",
                "tenant_id", "tenant_customer_a",
                "tenant_type", "USER_POOL_TENANT",
                "parent_tenant_id", "tenant-1",
                "user_pool_id", "pool-1"));
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    ActorContext actor =
        new JwtAuthenticationActorContextResolver()
            .resolve(new MockHttpServletRequest())
            .orElseThrow();

    assertEquals("tenant_customer_a", actor.tenantId());
    assertEquals("USER_POOL_TENANT", actor.tenantType());
    assertEquals("tenant-1", actor.parentTenantId());
    assertEquals("tenant_customer_a", actor.customerTenantId());
  }

  @Test
  void actorTypeParsesStableClaimValues() {
    assertEquals(ActorType.INTERNAL_ADMIN, ActorType.fromClaim("internal_admin"));
    assertEquals(ActorType.CONSOLE_USER, ActorType.fromClaim("console_user"));
    assertEquals(ActorType.END_USER, ActorType.fromClaim("end_user"));
    assertEquals(ActorType.DEVELOPER, ActorType.fromClaim("developer"));
    assertThrows(IllegalArgumentException.class, () -> ActorType.fromClaim("unknown"));
  }
}
