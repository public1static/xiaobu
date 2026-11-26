package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerTransaction;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;

public record LedgerTransactionResult(
    String id,
    TransactionDirection direction,
    long amountCents,
    LocalDate bookedOn,
    String categoryCode,
    String note,
    String accountId,
    String originalTransactionId) {

  static LedgerTransactionResult from(LedgerTransaction transaction) {
    var details = transaction.details();
    return new LedgerTransactionResult(
        transaction.id(),
        details.direction(),
        details.amountCents(),
        details.bookedOn(),
        details.categoryCode(),
        details.note(),
        details.accountId(),
        null);
  }

  static LedgerTransactionResult fromRefund(LedgerRefundActivity refund) {
    return new LedgerTransactionResult(
        refund.id(),
        TransactionDirection.EXPENSE,
        refund.amountCents(),
        refund.receivedOn(),
        refund.categoryCode(),
        refund.note(),
        refund.accountId(),
        refund.transactionId());
  }
}
