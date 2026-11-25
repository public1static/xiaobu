package com.xiaobu.identity.interfaces;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

public record LocalSessionContext(String actorId, String displayName, String ledgerId) {

  private static final String ATTRIBUTE = LocalSessionContext.class.getName();

  static void open(HttpServletRequest request, LocalSessionContext context) {
    close(request);
    request.getSession(true).setAttribute(ATTRIBUTE, context);
  }

  static void close(HttpServletRequest request) {
    var session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
  }

  public static LocalSessionContext require(HttpServletRequest request) {
    var session = request.getSession(false);
    if (session != null && session.getAttribute(ATTRIBUTE) instanceof LocalSessionContext context) {
      return context;
    }
    throw new FoundationException(
        new ErrorCode("XIAOBU_SESSION_REQUIRED"),
        ErrorCategory.UNAUTHENTICATED,
        "请先登录测试身份",
        Map.of());
  }
}
