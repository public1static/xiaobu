package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerAccountTransfer;
import java.time.LocalDate;

public record LedgerAccountTransferResult(
    String id,
    String sourceAccountId,
    String targetAccountId,
    long amountCents,
    LocalDate bookedOn,
    String note) {

  static LedgerAccountTransferResult from(LedgerAccountTransfer transfer) {
    return new LedgerAccountTransferResult(
        transfer.id(),
        transfer.sourceAccountId(),
        transfer.targetAccountId(),
        transfer.amountCents(),
        transfer.bookedOn(),
        transfer.note());
  }
}
