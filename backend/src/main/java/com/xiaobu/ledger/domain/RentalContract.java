package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;

public record RentalContract(
    String id,
    String ledgerId,
    String homeId,
    String landlordName,
    LocalDate startsOn,
    LocalDate endsOn,
    long monthlyRentCents,
    long depositCents,
    RentalPaymentCycle cycle,
    String billId,
    String note,
    int remindDays,
    LocalDate handledEnd,
    String previousContractId) {
  private static final long MAX_CENTS = 9_999_999_999L;

  public RentalContract {
    RentalExpiryReminder.requireLeadDays(remindDays);
    landlordName = landlordName == null ? "" : landlordName.strip();
    note = note == null ? "" : note.strip();
    if (landlordName.isEmpty()
        || landlordName.length() > 40
        || note.length() > 100
        || startsOn == null
        || endsOn == null
        || startsOn.getYear() < 1900
        || endsOn.getYear() > 2100
        || endsOn.isBefore(startsOn)
        || cycle == null
        || monthlyRentCents < 1
        || monthlyRentCents > MAX_CENTS / cycle.months()
        || depositCents < 0
        || depositCents > MAX_CENTS) {
      throw invalid("请检查出租方、租期、金额和付款周期");
    }
  }

  public long periodAmountCents() {
    return monthlyRentCents * cycle.months();
  }

  public RentalContract withBill(String billId) {
    return new RentalContract(
        id,
        ledgerId,
        homeId,
        landlordName,
        startsOn,
        endsOn,
        monthlyRentCents,
        depositCents,
        cycle,
        billId,
        note,
        remindDays,
        handledEnd,
        previousContractId);
  }

  public FixedBill createRentBill(String homeName, LocalDate firstDueOn) {
    if (firstDueOn == null || firstDueOn.isBefore(startsOn) || firstDueOn.isAfter(endsOn)) {
      throw invalid("首次付款日需在租期内");
    }
    var label = homeName + " · 房租";
    if (label.length() > 40) {
      int end = 35;
      if (Character.isHighSurrogate(homeName.charAt(end - 1))) end--;
      label = homeName.substring(0, end) + " · 房租";
    }
    return new FixedBill(
        billId,
        ledgerId,
        label,
        firstDueOn,
        periodAmountCents(),
        LedgerCategoryDefaults.HOUSING.name(),
        null,
        null,
        cycle.months(),
        endsOn,
        startsOn);
  }

  private static FoundationException invalid(String message) {
    return new FoundationException(new ErrorCode("XIAOBU_RENTAL_CONTRACT_INVALID"), message);
  }
}
