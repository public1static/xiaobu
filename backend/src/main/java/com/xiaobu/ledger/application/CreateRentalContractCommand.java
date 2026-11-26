package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.RentalPaymentCycle;
import java.time.LocalDate;

public record CreateRentalContractCommand(
    String ledgerId,
    String actorId,
    String homeId,
    String landlordName,
    LocalDate startsOn,
    LocalDate endsOn,
    long monthlyRentCents,
    long depositCents,
    RentalPaymentCycle cycle,
    LocalDate firstDueOn,
    String note,
    String existingBillId) {
  public CreateRentalContractCommand(
      String ledgerId,
      String actorId,
      String homeId,
      String landlordName,
      LocalDate startsOn,
      LocalDate endsOn,
      long monthlyRentCents,
      long depositCents,
      RentalPaymentCycle cycle,
      LocalDate firstDueOn,
      String note) {
    this(
        ledgerId,
        actorId,
        homeId,
        landlordName,
        startsOn,
        endsOn,
        monthlyRentCents,
        depositCents,
        cycle,
        firstDueOn,
        note,
        null);
  }
}
