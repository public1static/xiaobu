package com.xiaobu.gift.application;

import com.xiaobu.gift.domain.GiftDetail;
import com.xiaobu.ledger.application.TransactionExtensionData;

public record GiftDetailResult(String bookCode, String bookName, String personName, String reason)
    implements TransactionExtensionData {

  static GiftDetailResult from(GiftDetail detail) {
    return new GiftDetailResult(
        detail.book().name(), detail.book().displayName(), detail.personName(), detail.reason());
  }
}
