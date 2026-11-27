package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.RentalDeposit;
import com.xiaobu.ledger.domain.RentalDepositKind;
import java.time.LocalDate;

public record RentalDepositResult(
    String id,
    RentalDepositKind kind,
    long amountCents,
    LocalDate bookedOn,
    String accountId,
    String accountName,
    String note,
    long version,
    boolean canModify) {
  static RentalDepositResult from(RentalDeposit deposit, String accountName, boolean canModify) {
    return new RentalDepositResult(
        deposit.id(),
        deposit.kind(),
        deposit.amountCents(),
        deposit.bookedOn(),
        deposit.accountId(),
        accountName,
        deposit.note(),
        deposit.version(),
        canModify);
  }
}
