package com.xiaobu.identity.interfaces;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.util.Arrays;

/** Synthetic accounts used only by the loopback H5 preview. */
public enum LocalPreviewAccount {
  ALPHA("alpha", "user_preview_alpha", "测试甲"),
  BETA("beta", "user_preview_beta", "测试乙");

  private final String selector;
  private final String actorId;
  private final String displayName;

  LocalPreviewAccount(String selector, String actorId, String displayName) {
    this.selector = selector;
    this.actorId = actorId;
    this.displayName = displayName;
  }

  static LocalPreviewAccount fromSelector(String selector) {
    return Arrays.stream(values())
        .filter(account -> account.selector.equals(selector))
        .findFirst()
        .orElseThrow(() ->
            new FoundationException(new ErrorCode("XIAOBU_UNKNOWN_PREVIEW_ACCOUNT"), "请选择有效的测试身份"));
  }

  public static String displayNameForActor(String actorId) {
    return Arrays.stream(values())
        .filter(account -> account.actorId.equals(actorId))
        .map(LocalPreviewAccount::displayName)
        .findFirst()
        .orElse("账本成员");
  }

  String actorId() {
    return actorId;
  }

  String displayName() {
    return displayName;
  }
}
