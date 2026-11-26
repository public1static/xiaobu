package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;
import java.util.List;

public record RecordLedgerTransactionCommand(
    String ledgerId,
    String actorId,
    TransactionDirection direction,
    long amountCents,
    LocalDate bookedOn,
    String categoryCode,
    String note,
    String accountId,
    List<TransactionExtensionChange> extensions) {

  public RecordLedgerTransactionCommand(
      String ledgerId,
      String actorId,
      TransactionDirection direction,
      long amountCents,
      LocalDate bookedOn,
      String categoryCode,
      String note,
      String accountId) {
    this(
        ledgerId,
        actorId,
        direction,
        amountCents,
        bookedOn,
        categoryCode,
        note,
        accountId,
        List.of());
  }
}
