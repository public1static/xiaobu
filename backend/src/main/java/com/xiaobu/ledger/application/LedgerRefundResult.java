package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerRefund;
import java.time.LocalDate;

public record LedgerRefundResult(
    String id, long amountCents, LocalDate receivedOn, String accountId, String accountName) {

  static LedgerRefundResult from(LedgerRefund refund, String accountName) {
    return new LedgerRefundResult(
        refund.id(), refund.amountCents(), refund.receivedOn(), refund.accountId(), accountName);
  }
}
