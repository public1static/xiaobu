package com.xiaobu.ledger.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

public final class RentalDepositTimeline {
  private RentalDepositTimeline() {}

  public static boolean accepts(Map<LocalDate, Long> dailyChanges, RentalDeposit candidate) {
    var timeline = new TreeMap<>(dailyChanges);
    timeline.merge(
        candidate.bookedOn(), candidate.kind().changeInHeld(candidate.amountCents()), Long::sum);
    long held = 0;
    for (long change : timeline.values()) {
      held += change;
      if (held < 0) {
        return false;
      }
    }
    return true;
  }
}
