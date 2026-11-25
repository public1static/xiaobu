package com.aegisid.foundation.security.actor;

import com.aegisid.foundation.shared.text.Texts;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ActorContext(
    ActorType actorType,
    String actorId,
    String memberId,
    String userId,
    String subject,
    String tenantId,
    String tenantType,
    String parentTenantId,
    String userPoolId,
    String applicationId,
    String clientId,
    String customerTenantId,
    String customerTenantCode,
    String customerTenantName,
    List<String> authorities,
    Optional<Instant> tokenIssuedAt) {

  public ActorContext {
    actorType = Objects.requireNonNull(actorType, "actorType must not be null");
    actorId = Texts.require(actorId, "actorId");
    memberId = Texts.trimToNull(memberId);
    userId = Texts.trimToNull(userId);
    subject = Texts.trimToNull(subject);
    tenantId = Texts.trimToNull(tenantId);
    tenantType = Texts.trimToNull(tenantType);
    parentTenantId = Texts.trimToNull(parentTenantId);
    userPoolId = Texts.trimToNull(userPoolId);
    applicationId = Texts.trimToNull(applicationId);
    clientId = Texts.trimToNull(clientId);
    customerTenantId = Texts.trimToNull(customerTenantId);
    customerTenantCode = Texts.trimToNull(customerTenantCode);
    customerTenantName = Texts.trimToNull(customerTenantName);
    authorities = authorities == null ? List.of() : List.copyOf(authorities);
    tokenIssuedAt = tokenIssuedAt == null ? Optional.empty() : tokenIssuedAt;
  }

  public ActorContext(
      ActorType actorType,
      String actorId,
      String memberId,
      String userId,
      String subject,
      String tenantId,
      String tenantType,
      String parentTenantId,
      String userPoolId,
      String applicationId,
      String clientId,
      List<String> authorities) {
    this(
        actorType,
        actorId,
        memberId,
        userId,
        subject,
        tenantId,
        tenantType,
        parentTenantId,
        userPoolId,
        applicationId,
        clientId,
        null,
        null,
        null,
        authorities,
        Optional.empty());
  }

  public ActorContext(
      ActorType actorType,
      String actorId,
      String memberId,
      String userId,
      String subject,
      String tenantId,
      String tenantType,
      String parentTenantId,
      String userPoolId,
      String applicationId,
      String clientId,
      String customerTenantId,
      String customerTenantCode,
      List<String> authorities) {
    this(
        actorType,
        actorId,
        memberId,
        userId,
        subject,
        tenantId,
        tenantType,
        parentTenantId,
        userPoolId,
        applicationId,
        clientId,
        customerTenantId,
        customerTenantCode,
        null,
        authorities);
  }

  public ActorContext(
      ActorType actorType,
      String actorId,
      String memberId,
      String userId,
      String subject,
      String tenantId,
      String tenantType,
      String parentTenantId,
      String userPoolId,
      String applicationId,
      String clientId,
      String customerTenantId,
      String customerTenantCode,
      String customerTenantName,
      List<String> authorities) {
    this(
        actorType,
        actorId,
        memberId,
        userId,
        subject,
        tenantId,
        tenantType,
        parentTenantId,
        userPoolId,
        applicationId,
        clientId,
        customerTenantId,
        customerTenantCode,
        customerTenantName,
        authorities,
        Optional.empty());
  }

  public ActorContext(
      ActorType actorType,
      String actorId,
      String memberId,
      String userId,
      String subject,
      String tenantId,
      String userPoolId,
      String applicationId,
      String clientId,
      List<String> authorities) {
    this(
        actorType,
        actorId,
        memberId,
        userId,
        subject,
        tenantId,
        null,
        null,
        userPoolId,
        applicationId,
        clientId,
        null,
        null,
        null,
        authorities,
        Optional.empty());
  }

  public ActorContext(String actorId, String tenantId, List<String> authorities) {
    this(ActorType.ANONYMOUS, actorId, null, null, null, tenantId, null, null, null, authorities);
  }

  public ActorContext(
      ActorType actorType,
      String actorId,
      String userId,
      String subject,
      String tenantId,
      String tenantType,
      String parentTenantId,
      String userPoolId,
      String applicationId,
      String clientId,
      List<String> authorities) {
    this(
        actorType,
        actorId,
        null,
        userId,
        subject,
        tenantId,
        tenantType,
        parentTenantId,
        userPoolId,
        applicationId,
        clientId,
        authorities);
  }

  public ActorContext(
      ActorType actorType,
      String actorId,
      String userId,
      String subject,
      String tenantId,
      String userPoolId,
      String applicationId,
      String clientId,
      List<String> authorities) {
    this(
        actorType,
        actorId,
        null,
        userId,
        subject,
        tenantId,
        null,
        null,
        userPoolId,
        applicationId,
        clientId,
        authorities);
  }

  public static ActorContext anonymous() {
    return new ActorContext(
        ActorType.ANONYMOUS,
        "anonymous",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        Optional.empty());
  }

  public boolean hasUserId() {
    return userId != null && !userId.isBlank();
  }
}
