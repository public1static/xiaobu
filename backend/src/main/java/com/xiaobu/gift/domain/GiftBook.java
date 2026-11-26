package com.xiaobu.gift.domain;

import com.xiaobu.ledger.domain.TransactionDirection;

public enum GiftBook {
  DAILY_RECEIVED("日常收礼"),
  DAILY_SENT("日常送礼");

  private final String name;

  GiftBook(String name) {
    this.name = name;
  }

  public String displayName() {
    return name;
  }

  public static GiftBook dailyFor(TransactionDirection direction) {
    return direction == TransactionDirection.INCOME ? DAILY_RECEIVED : DAILY_SENT;
  }
}
