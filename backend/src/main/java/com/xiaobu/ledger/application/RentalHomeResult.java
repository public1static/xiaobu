package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.RentalHome;

public record RentalHomeResult(
    String id, String name, String address, String note, long contractCount) {
  public static RentalHomeResult from(RentalHome home, long contractCount) {
    return new RentalHomeResult(home.id(), home.name(), home.address(), home.note(), contractCount);
  }
}
