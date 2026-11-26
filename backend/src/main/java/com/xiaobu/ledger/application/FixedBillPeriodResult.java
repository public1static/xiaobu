package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.FixedBill;
import com.xiaobu.ledger.domain.FixedBillPayment;
import java.time.LocalDate;
import java.time.YearMonth;

public record FixedBillPeriodResult(
    String billId,
    String name,
    LocalDate dueOn,
    long amountCents,
    String categoryCode,
    String accountId,
    String transactionId,
    LocalDate paidOn,
    Long paidAmountCents) {
  public static FixedBillPeriodResult from(
      FixedBill bill, YearMonth month, FixedBillPayment payment) {
    return new FixedBillPeriodResult(
        bill.id(),
        bill.name(),
        bill.dueOn(month),
        bill.amountCents(),
        bill.categoryCode(),
        bill.accountId(),
        payment == null ? null : payment.transactionId(),
        payment == null ? null : payment.paidOn(),
        payment == null ? null : payment.amountCents());
  }
}
