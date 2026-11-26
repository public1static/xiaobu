package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;
import java.util.Set;

public final class RentalExpiryReminder {
  public static final int DEFAULT_DAYS = 30;
  private static final Set<Integer> LEAD_DAYS = Set.of(0, 7, 15, DEFAULT_DAYS);

  private RentalExpiryReminder() {}

  public static int requireLeadDays(int days) {
    if (!LEAD_DAYS.contains(days)) {
      throw new FoundationException(new ErrorCode("XIAOBU_RENTAL_REMINDER_DAYS"), "请选择有效的提醒天数");
    }
    return days;
  }

  public static boolean isPending(
      LocalDate endsOn, int days, LocalDate handledEnd, LocalDate today) {
    // Even with advance reminders off, the contract becomes actionable on its expiry date.
    return !endsOn.equals(handledEnd) && !endsOn.isAfter(today.plusDays(days));
  }
}
