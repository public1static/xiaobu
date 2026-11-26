package com.xiaobu.ledger.domain;

import java.time.LocalDate;
import java.time.YearMonth;

public enum ManualReminderFrequency {
  ONCE(0, 0),
  DAILY(1, 0),
  WEEKLY(7, 0),
  FORTNIGHTLY(14, 0),
  MONTHLY(0, 1),
  QUARTERLY(0, 3),
  HALF_YEARLY(0, 6),
  YEARLY(0, 12);

  private final int days;
  private final int months;

  ManualReminderFrequency(int days, int months) {
    this.days = days;
    this.months = months;
  }

  LocalDate nextDueOn(LocalDate anchor, LocalDate dueOn) {
    if (this == ONCE) return dueOn;
    if (days > 0) return dueOn.plusDays(days);
    var nextMonth = YearMonth.from(dueOn).plusMonths(months);
    return nextMonth.atDay(Math.min(anchor.getDayOfMonth(), nextMonth.lengthOfMonth()));
  }
}
