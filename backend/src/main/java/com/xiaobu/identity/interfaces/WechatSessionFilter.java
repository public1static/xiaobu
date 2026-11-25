package com.xiaobu.identity.interfaces;

import com.xiaobu.identity.application.WechatSessionApplicationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("wechat-preview")
class WechatSessionFilter extends OncePerRequestFilter {

  private final WechatSessionApplicationService sessions;

  WechatSessionFilter(
      WechatSessionApplicationService sessions,
      Environment environment,
      @Value("${server.address:}") String address) {
    if (environment.matchesProfiles("local-expense") || !"127.0.0.1".equals(address)) {
      throw new IllegalStateException(
          "WeChat preview must be isolated on loopback and not run with local-expense");
    }
    this.sessions = sessions;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getServletPath().startsWith("/api/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var session =
        sessions
            .findByAuthorization(request.getHeader("Authorization"))
            .map(verified ->
                new LedgerSessionContext(
                    verified.actorId(), verified.displayName(), verified.ledgerId()))
            .orElse(null);
    LedgerSessionContext.useVerifiedBearerSession(request, session);
    response.setHeader("Cache-Control", "no-store");
    chain.doFilter(request, response);
  }
}
