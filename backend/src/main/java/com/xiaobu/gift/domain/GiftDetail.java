package com.xiaobu.gift.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;

public record GiftDetail(
    GiftBook book,
    String bookId,
    String contactId,
    String personName,
    String distinction,
    String reason) {
  public GiftDetail {
    personName = optionalText(personName);
    distinction = optionalText(distinction);
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
