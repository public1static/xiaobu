package com.aegisid.foundation.security.web;

import com.aegisid.foundation.security.actor.ActorContext;
import com.aegisid.foundation.security.actor.ActorType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class JwtAuthenticationActorContextResolver implements ActorContextResolver {

  @Override
  public Optional<ActorContext> resolve(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
        || !(jwtAuthentication.getPrincipal() instanceof Jwt jwt)) {
      return Optional.empty();
    }
    String actorType = jwt.getClaimAsString("actor_type");
    String userId = jwt.getClaimAsString("user_id");
    if (actorType == null || userId == null || actorType.isBlank() || userId.isBlank()) {
      return Optional.empty();
    }
    String tenantId = jwt.getClaimAsString("tenant_id");
    String tenantType = jwt.getClaimAsString("tenant_type");
    String parentTenantId = jwt.getClaimAsString("parent_tenant_id");
    String selectedTenantCode = jwt.getClaimAsString("tenant_code");
    String selectedTenantName = jwt.getClaimAsString("tenant_name");
    String selectedTenantId =
        isSelectedTenant(tenantType, parentTenantId, selectedTenantCode, selectedTenantName)
            ? tenantId
            : null;
    return Optional.of(
        new ActorContext(
            ActorType.fromClaim(actorType),
            userId,
            jwt.getClaimAsString("member_id"),
            userId,
            jwt.getSubject(),
            tenantId,
            tenantType,
            parentTenantId,
            jwt.getClaimAsString("user_pool_id"),
            jwt.getClaimAsString("application_id"),
            jwt.getClaimAsString("client_id"),
            selectedTenantId,
            selectedTenantCode,
            selectedTenantName,
            List.of(),
            Optional.ofNullable(jwt.getIssuedAt())));
  }

  private static boolean isSelectedTenant(
      String tenantType,
      String parentTenantId,
      String selectedTenantCode,
      String selectedTenantName) {
    return selectedTenantCode != null
        || selectedTenantName != null
        || ("USER_POOL_TENANT".equals(tenantType) && parentTenantId != null);
  }
}
