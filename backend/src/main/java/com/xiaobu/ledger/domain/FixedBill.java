package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;
import java.time.YearMonth;

public record FixedBill(
    String id,
    String ledgerId,
    String name,
    LocalDate firstDueOn,
    long amountCents,
    String categoryCode,
    String accountId,
    LocalDate stopMonth) {
  public FixedBill {
    name = name == null ? "" : name.strip();
    if (name.isEmpty()
        || name.length() > 40
        || firstDueOn == null
        || firstDueOn.getYear() < 1900
        || firstDueOn.getYear() > 9999
        || amountCents < 1
        || amountCents > 9_999_999_999L) {
      throw new FoundationException(new ErrorCode("XIAOBU_FIXED_BILL_INVALID"), "请检查账单名称、金额和首次到期日");
    }
  }

  public FixedBill(
      String id,
      String ledgerId,
      String name,
      LocalDate firstDueOn,
      long amountCents,
      String categoryCode,
      String accountId) {
    this(id, ledgerId, name, firstDueOn, amountCents, categoryCode, accountId, null);
  }

  public boolean covers(YearMonth month) {
    return !firstDueOn.isAfter(month.atEndOfMonth())
        && (stopMonth == null || month.atDay(1).isBefore(stopMonth));
  }

  public LocalDate earliestRevision(YearMonth currentMonth, LocalDate latestProtected) {
    var boundary = latestProtected == null ? currentMonth : YearMonth.from(latestProtected);
    if (boundary.isBefore(currentMonth)) boundary = currentMonth;
    var openingMonth = YearMonth.from(firstDueOn);
    return dueOn(openingMonth.isAfter(boundary) ? openingMonth : boundary.plusMonths(1));
  }

  public LocalDate dueOn(YearMonth month) {
    return month.atDay(Math.min(firstDueOn.getDayOfMonth(), month.lengthOfMonth()));
  }
}
