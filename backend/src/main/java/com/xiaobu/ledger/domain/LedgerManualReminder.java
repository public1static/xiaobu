package com.xiaobu.ledger.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

public record LedgerManualReminder(
    String id,
    String title,
    LocalDate dueOn,
    LocalDateTime remindAt,
    boolean completed,
    ManualReminderFrequency frequency,
    LocalDate anchorOn,
    long revision,
    boolean stopped) {

  public static LedgerManualReminder schedule(
      String id,
      String title,
      LocalDate dueOn,
      LocalTime time,
      int leadDays,
      ManualReminderFrequency frequency) {
    return new LedgerManualReminder(
        id,
        title,
        dueOn,
        dueOn.minusDays(leadDays).atTime(time),
        false,
        frequency,
        dueOn,
        0,
        false);
  }

  public int leadDays() {
    return (int) ChronoUnit.DAYS.between(remindAt.toLocalDate(), dueOn);
  }

  public boolean open() {
    return !completed && !stopped;
  }

  public LedgerManualReminder revise(LedgerManualReminder replacement) {
    var anchor =
        dueOn.equals(replacement.dueOn) && frequency == replacement.frequency
            ? anchorOn
            : replacement.dueOn;
    return new LedgerManualReminder(
        id,
        replacement.title,
        replacement.dueOn,
        replacement.remindAt,
        false,
        replacement.frequency,
        anchor,
        revision + 1,
        false);
  }

  public LedgerManualReminder advance() {
    var next = frequency.nextDueOn(anchorOn, dueOn);
    if (frequency == ManualReminderFrequency.ONCE || next.getYear() > 2100)
      return new LedgerManualReminder(
          id, title, dueOn, remindAt, true, frequency, anchorOn, revision + 1, false);
    return new LedgerManualReminder(
        id,
        title,
        next,
        next.minusDays(leadDays()).atTime(remindAt.toLocalTime()),
        false,
        frequency,
        anchorOn,
        revision + 1,
        false);
  }

  public LedgerManualReminder resumeOccurrence(LedgerManualReminder completedOccurrence) {
    return new LedgerManualReminder(
        id,
        completedOccurrence.title,
        completedOccurrence.dueOn,
        completedOccurrence.remindAt,
        false,
        frequency,
        anchorOn,
        revision + 1,
        false);
  }

  public LedgerManualReminder stop() {
    return new LedgerManualReminder(
        id, title, dueOn, remindAt, completed, frequency, anchorOn, revision + 1, true);
  }
}
