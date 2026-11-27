package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.FixedBill;
import java.time.LocalDate;
import java.time.YearMonth;

public record FixedBillPlanResult(
    String billId,
    String name,
    LocalDate firstDueOn,
    LocalDate earliestFirstDueOn,
    long amountCents,
    String categoryCode,
    String accountId) {
  public static FixedBillPlanResult from(
      FixedBill bill, YearMonth currentMonth, LocalDate latestProtected) {
    return new FixedBillPlanResult(
        bill.id(),
        bill.name(),
        bill.firstDueOn(),
        bill.earliestRevision(currentMonth, latestProtected),
        bill.amountCents(),
        bill.categoryCode(),
        bill.accountId());
  }
}
