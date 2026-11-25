package com.aegisid.foundation.security.web;

import com.aegisid.foundation.security.actor.ActorContext;
import com.aegisid.foundation.security.actor.ActorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class ActorTypeAuthorizationFilter extends OncePerRequestFilter {

  public static final String ACTOR_CONTEXT_REQUEST_ATTRIBUTE = ActorContext.class.getName();
  private static final String API_PREFIX = "/api/v1/";
  private static final Map<String, ActorType> REQUIRED_TYPES =
      Map.of(
          "/api/v1/internal-admin/", ActorType.INTERNAL_ADMIN,
          "/api/v1/internal/", ActorType.INTERNAL_ADMIN,
          "/api/v1/account-center/", ActorType.CONSOLE_USER,
          "/api/v1/console/", ActorType.CONSOLE_USER,
          "/api/v1/developer/", ActorType.DEVELOPER,
          "/api/v1/end-user/", ActorType.END_USER);
  private static final Set<String> PUBLIC_PATH_PREFIXES = Set.of("/api/v1/login/");
  private static final Set<String> PUBLIC_ENDPOINTS =
      Set.of("POST /api/v1/console/members/invitations/accept");

  private final ActorContextResolver actorContextResolver;

  public ActorTypeAuthorizationFilter(ActorContextResolver actorContextResolver) {
    this.actorContextResolver =
        java.util.Objects.requireNonNull(
            actorContextResolver, "actorContextResolver must not be null");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (isPublicEndpoint(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    ActorType requiredType = requiredType(request.getRequestURI());
    if (requiredType == null) {
      if (request.getRequestURI().startsWith(API_PREFIX) && !isPublicPathPrefix(request)) {
        writeError(response, HttpServletResponse.SC_FORBIDDEN, "API_ENTRY_FORBIDDEN");
        return;
      }
      filterChain.doFilter(request, response);
      return;
    }

    Optional<ActorContext> actor = actorContextResolver.resolve(request);
    if (actor.isEmpty()) {
      writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "ACTOR_CONTEXT_REQUIRED");
      return;
    }
    if (actor.orElseThrow().actorType() != requiredType) {
      writeError(response, HttpServletResponse.SC_FORBIDDEN, "ACTOR_TYPE_FORBIDDEN");
      return;
    }

    request.setAttribute(ACTOR_CONTEXT_REQUEST_ATTRIBUTE, actor.orElseThrow());
    filterChain.doFilter(request, response);
  }

  private static boolean isPublicEndpoint(HttpServletRequest request) {
    return PUBLIC_ENDPOINTS.contains(request.getMethod() + " " + request.getRequestURI());
  }

  private static boolean isPublicPathPrefix(HttpServletRequest request) {
    return PUBLIC_PATH_PREFIXES.stream()
        .anyMatch(prefix -> request.getRequestURI().startsWith(prefix));
  }

  private static ActorType requiredType(String requestUri) {
    for (Map.Entry<String, ActorType> entry : REQUIRED_TYPES.entrySet()) {
      if (requestUri.startsWith(entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static void writeError(HttpServletResponse response, int status, String code)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"" + code + "\"}}");
  }
}
