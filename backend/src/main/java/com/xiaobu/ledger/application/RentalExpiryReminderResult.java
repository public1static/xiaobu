package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.RentalExpiryReminder;
import java.time.LocalDate;

public record RentalExpiryReminderResult(
    String contractId,
    String homeName,
    String landlordName,
    LocalDate endsOn,
    int remindDays,
    LocalDate handledEnd) {
  public boolean isPending(LocalDate today) {
    return RentalExpiryReminder.isPending(endsOn, remindDays, handledEnd, today);
  }
}
