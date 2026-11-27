package com.xiaobu.ledger.domain;

public enum RentalPaymentCycle {
  MONTHLY(1),
  QUARTERLY(3),
  SEMIANNUAL(6),
  ANNUAL(12);

  private final int months;

  RentalPaymentCycle(int months) {
    this.months = months;
  }

  public int months() {
    return months;
  }
}
