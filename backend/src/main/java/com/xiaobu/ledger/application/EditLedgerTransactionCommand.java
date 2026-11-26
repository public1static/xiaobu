package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;
import java.util.List;

public record EditLedgerTransactionCommand(
    String ledgerId,
    String actorId,
    String transactionId,
    long expectedVersion,
    TransactionDirection direction,
    long amountCents,
    LocalDate bookedOn,
    String categoryCode,
    String note,
    String accountId,
    List<TransactionExtensionChange> extensions) {

  public EditLedgerTransactionCommand(
      String ledgerId,
      String actorId,
      String transactionId,
      long expectedVersion,
      TransactionDirection direction,
      long amountCents,
      LocalDate bookedOn,
      String categoryCode,
      String note,
      String accountId) {
    this(
        ledgerId,
        actorId,
        transactionId,
        expectedVersion,
        direction,
        amountCents,
        bookedOn,
        categoryCode,
        note,
        accountId,
        List.of());
  }
}
