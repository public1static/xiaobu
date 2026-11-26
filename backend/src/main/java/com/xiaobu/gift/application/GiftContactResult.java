package com.xiaobu.gift.application;

import com.xiaobu.gift.domain.GiftContact;

public record GiftContactResult(String id, String name, String distinction) {
  static GiftContactResult from(GiftContact item) {
    return new GiftContactResult(item.id(), item.name(), item.distinction());
  }
}
