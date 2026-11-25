package com.aegisid.foundation.security.web;

import com.aegisid.foundation.security.actor.ActorContext;
import com.aegisid.foundation.security.actor.ActorType;
import com.aegisid.foundation.shared.text.Texts;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;

public class HeaderActorContextResolver implements ActorContextResolver {

  public static final String ACTOR_TYPE_HEADER = "X-Aegis-Actor-Type";
  public static final String USER_ID_HEADER = "X-Aegis-Current-User-Id";
  public static final String TENANT_ID_HEADER = "X-Aegis-Current-Tenant-Id";
  public static final String USER_POOL_ID_HEADER = "X-Aegis-Current-User-Pool-Id";
  public static final String APPLICATION_ID_HEADER = "X-Aegis-Current-Application-Id";
  public static final String CLIENT_ID_HEADER = "X-Aegis-Current-Client-Id";

  @Override
  public Optional<ActorContext> resolve(HttpServletRequest request) {
    String actorType = header(request, ACTOR_TYPE_HEADER);
    String userId = header(request, USER_ID_HEADER);
    if (actorType == null || userId == null) {
      return Optional.empty();
    }
    return Optional.of(
        new ActorContext(
            ActorType.fromClaim(actorType),
            userId,
            userId,
            null,
            header(request, TENANT_ID_HEADER),
            header(request, USER_POOL_ID_HEADER),
            header(request, APPLICATION_ID_HEADER),
            header(request, CLIENT_ID_HEADER),
            List.of()));
  }

  private static String header(HttpServletRequest request, String name) {
    String value = request.getHeader(name);
    return Texts.trimToNull(value);
  }
}
