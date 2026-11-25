package com.xiaobu.identity.interfaces;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

public record LedgerSessionContext(String actorId, String displayName, String ledgerId) {

  private static final String ATTRIBUTE = LedgerSessionContext.class.getName();
  private static final String BEARER_MODE = ATTRIBUTE + ".bearer";
  private static final String VERIFIED_BEARER = ATTRIBUTE + ".verifiedBearer";

  static void open(HttpServletRequest request, LedgerSessionContext context) {
    close(request);
    request.getSession(true).setAttribute(ATTRIBUTE, context);
  }

  static void close(HttpServletRequest request) {
    var session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
  }

  static void useVerifiedBearerSession(HttpServletRequest request, LedgerSessionContext session) {
    request.setAttribute(BEARER_MODE, true);
    request.setAttribute(VERIFIED_BEARER, session);
  }

  public static LedgerSessionContext current(HttpServletRequest request) {
    if (Boolean.TRUE.equals(request.getAttribute(BEARER_MODE))) {
      return (LedgerSessionContext) request.getAttribute(VERIFIED_BEARER);
    }
    var session = request.getSession(false);
    if (session != null
        && session.getAttribute(ATTRIBUTE) instanceof LedgerSessionContext context) {
      return context;
    }
    return null;
  }

  public static LedgerSessionContext require(HttpServletRequest request) {
    var context = current(request);
    if (context != null) {
      return context;
    }
    throw new FoundationException(
        new ErrorCode("XIAOBU_SESSION_REQUIRED"), ErrorCategory.UNAUTHENTICATED, "请先登录", Map.of());
  }
}
