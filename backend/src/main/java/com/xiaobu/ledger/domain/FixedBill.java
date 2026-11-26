package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Set;

public record FixedBill(
    String id,
    String ledgerId,
    String name,
    LocalDate firstDueOn,
    long amountCents,
    String categoryCode,
    String accountId,
    LocalDate stopMonth,
    int cycleMonths,
    LocalDate lastDueOn,
    LocalDate rentalStartsOn) {
  public FixedBill {
    name = name == null ? "" : name.strip();
    if (name.isEmpty()
        || name.length() > 40
        || firstDueOn == null
        || firstDueOn.getYear() < 1900
        || firstDueOn.getYear() > 9999
        || !Set.of(1, 3, 6, 12).contains(cycleMonths)
        || (lastDueOn != null && lastDueOn.isBefore(firstDueOn))
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
    this(id, ledgerId, name, firstDueOn, amountCents, categoryCode, accountId, null, 1, null, null);
  }

  public boolean covers(YearMonth month) {
    long offset = ChronoUnit.MONTHS.between(YearMonth.from(firstDueOn), month);
    return offset >= 0
        && offset % cycleMonths == 0
        && (stopMonth == null || month.atDay(1).isBefore(stopMonth))
        && (lastDueOn == null || !dueOn(month).isAfter(lastDueOn))
        && (rentalStartsOn == null || !dueOn(month).isBefore(rentalStartsOn));
  }

  public LocalDate firstDueOnOrAfter(LocalDate start) {
    var anchor = YearMonth.from(firstDueOn);
    var offset = Math.max(0, ChronoUnit.MONTHS.between(anchor, YearMonth.from(start)));
    var next = anchor.plusMonths(((offset + cycleMonths - 1) / cycleMonths) * cycleMonths);
    if (dueOn(next).isBefore(start)) next = next.plusMonths(cycleMonths);
    return dueOn(next);
  }

  public FixedBill adoptForRental(LocalDate start, LocalDate end, long amountCents) {
    return new FixedBill(
        id,
        ledgerId,
        name,
        firstDueOn,
        amountCents,
        categoryCode,
        accountId,
        stopMonth,
        cycleMonths,
        end,
        start);
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
