package com.xiaobu.gift.application;

import com.xiaobu.gift.domain.GiftActivityBook;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;

public record GiftBookResult(
    String id,
    String name,
    TransactionDirection direction,
    LocalDate defaultDate,
    String defaultReason,
    boolean archived) {
  static GiftBookResult from(GiftActivityBook item) {
    return new GiftBookResult(
        item.id(),
        item.name(),
        item.direction(),
        item.defaultDate(),
        item.defaultReason(),
        item.archived());
  }
}
