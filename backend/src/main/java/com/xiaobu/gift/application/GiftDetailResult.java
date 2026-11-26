package com.xiaobu.gift.application;

import com.xiaobu.gift.domain.GiftDetail;
import com.xiaobu.ledger.application.TransactionExtensionData;

public record GiftDetailResult(
    String bookCode,
    String bookName,
    String personName,
    String reason,
    String bookId,
    String contactId,
    String distinction)
    implements TransactionExtensionData {

  static GiftDetailResult from(GiftDetail detail, String bookName) {
    return new GiftDetailResult(
        detail.book().name(),
        bookName,
        detail.personName(),
        detail.reason(),
        detail.bookId(),
        detail.contactId(),
        detail.distinction());
  }
}
