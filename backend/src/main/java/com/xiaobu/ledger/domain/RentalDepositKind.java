package com.xiaobu.ledger.domain;

public enum RentalDepositKind {
  PAY(1),
  REFUND(-1);

  private final int heldSign;

  RentalDepositKind(int heldSign) {
    this.heldSign = heldSign;
  }

  public long changeInHeld(long amountCents) {
    return amountCents * heldSign;
  }
}
