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
    String existingBillId,
    Integer remindDays,
    String landlordContactId,
    String landlordDistinction) {
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
      String note,
      String existingBillId,
      Integer remindDays) {
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
        existingBillId,
        remindDays,
        null,
        null);
  }

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
      String note,
      String existingBillId) {
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
        existingBillId,
        null);
  }

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
