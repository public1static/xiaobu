package com.xiaobu.gift.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;

public record GiftDetail(GiftBook book, String personName, String reason) {
  public GiftDetail {
    personName = optionalText(personName);
    reason = optionalText(reason);
  }

  private static String optionalText(String value) {
    String text = value == null ? "" : value.strip();
    if (text.length() > 60) {
      throw new FoundationException(new ErrorCode("XIAOBU_GIFT_DETAIL_INVALID"), "姓名和事由最多 60 字");
    }
    return text.isEmpty() ? null : text;
  }
}
