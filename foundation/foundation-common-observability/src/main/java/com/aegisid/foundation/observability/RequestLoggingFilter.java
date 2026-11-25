package com.aegisid.foundation.observability;

import com.aegisid.foundation.shared.request.RequestContextView;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Emits conservative request logs without request bodies or sensitive headers. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

  public static final String REQUEST_CONTEXT_ATTRIBUTE_NAME =
      "com.aegisid.foundation.web.request.RequestContext";

  public static final String ACTOR_CONTEXT_ATTRIBUTE_NAME =
      "com.aegisid.foundation.security.actor.ActorContext";

  private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

  private final RequestContextMdcContributor mdcContributor;

  public RequestLoggingFilter(RequestContextMdcContributor mdcContributor) {
    this.mdcContributor = mdcContributor;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long startedAtNanos = System.nanoTime();
    mdcContributor.contribute(requestContextFrom(request));
    try {
      filterChain.doFilter(request, response);
    } finally {
      mdcContributor.contributeActor(actorContextFrom(request));
      long durationMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L;
      LOGGER.info(
          "http_request method={} path={} query={} status={} durationMs={}",
          request.getMethod(),
          request.getRequestURI(),
          redactQueryString(request.getQueryString()),
          response.getStatus(),
          durationMillis);
      mdcContributor.clear();
    }
  }

  private static RequestContextView requestContextFrom(HttpServletRequest request) {
    Object attribute = request.getAttribute(REQUEST_CONTEXT_ATTRIBUTE_NAME);
    if (attribute instanceof RequestContextView requestContext) {
      return requestContext;
    }
    return null;
  }

  private static Object actorContextFrom(HttpServletRequest request) {
    return request.getAttribute(ACTOR_CONTEXT_ATTRIBUTE_NAME);
  }

  public static String redactQueryString(String queryString) {
    if (queryString == null || queryString.isBlank()) {
      return "";
    }

    String[] parameters = queryString.split("&", -1);
    StringBuilder redacted = new StringBuilder(queryString.length());
    for (int index = 0; index < parameters.length; index++) {
      if (index > 0) {
        redacted.append('&');
      }
      redacted.append(redactParameter(parameters[index]));
    }
    return redacted.toString();
  }

  private static String redactParameter(String parameter) {
    int separatorIndex = parameter.indexOf('=');
    String name = separatorIndex >= 0 ? parameter.substring(0, separatorIndex) : parameter;
    if (isSensitiveName(name)) {
      return name + "=<redacted>";
    }
    return parameter;
  }

  private static boolean isSensitiveName(String name) {
    String decodedName = decodeNameOrRedactionMarker(name);
    if (decodedName == null) {
      return true;
    }

    String normalized = decodedName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    return normalized.contains("password")
        || normalized.contains("secret")
        || normalized.equals("code")
        || normalized.equals("codeverifier")
        || normalized.equals("state")
        || normalized.equals("nonce")
        || normalized.equals("relaystate")
        || normalized.equals("sessionstate")
        || normalized.equals("ticket")
        || normalized.contains("authorizationcode")
        || normalized.contains("assertion")
        || normalized.contains("samlrequest")
        || normalized.contains("samlresponse")
        || normalized.endsWith("token");
  }

  private static String decodeNameOrRedactionMarker(String name) {
    try {
      return URLDecoder.decode(name, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
