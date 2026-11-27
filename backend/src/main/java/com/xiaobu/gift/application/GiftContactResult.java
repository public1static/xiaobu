package com.xiaobu.gift.application;

import com.xiaobu.gift.domain.GiftContact;

public record GiftContactResult(String id, String name, String distinction) {
  public static GiftContactResult from(GiftContact item) {
    return new GiftContactResult(item.id(), item.name(), item.distinction());
  }
}
