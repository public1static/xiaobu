package com.aegisid.foundation.security.web;

import com.aegisid.foundation.security.actor.ActorContext;
import com.aegisid.foundation.security.actor.ActorType;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

public final class EnterpriseRequestActor {
  private EnterpriseRequestActor() {}

  public static ActorContext require(HttpServletRequest req) {
    if (req.getAttribute(ActorTypeAuthorizationFilter.ACTOR_CONTEXT_REQUEST_ATTRIBUTE)
            instanceof ActorContext actor
        && actor.actorType() == ActorType.END_USER
        && "USER_POOL_TENANT".equals(actor.tenantType())
        && actor.tenantId() != null
        && actor.tenantId().equals(actor.customerTenantId())) {
      String expectedTenant = req.getHeader("X-Workplace-Tenant");
      if (expectedTenant != null && !expectedTenant.equals(actor.tenantId())) {
        throw new FoundationException(
            new ErrorCode("WORKPLACE_TENANT_CHANGED"),
            ErrorCategory.CONFLICT,
            "当前企业已切换，请重新打开目录",
            Map.of());
      }
      return actor;
    }
    throw new FoundationException(
        new ErrorCode("WORKPLACE_ENTERPRISE_REQUIRED"),
        ErrorCategory.FORBIDDEN,
        "需要企业成员身份",
        Map.of());
  }
}
