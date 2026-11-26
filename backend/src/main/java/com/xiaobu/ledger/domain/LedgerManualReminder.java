package com.xiaobu.ledger.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record LedgerManualReminder(
    String id, String title, LocalDate dueOn, LocalDateTime remindAt, boolean completed) {

  public static LedgerManualReminder schedule(
      String id, String title, LocalDate dueOn, LocalTime time, int leadDays) {
    return new LedgerManualReminder(
        id, title, dueOn, dueOn.minusDays(leadDays).atTime(time), false);
  }

  public int leadDays() {
    return (int) java.time.temporal.ChronoUnit.DAYS.between(remindAt.toLocalDate(), dueOn);
  }
}
