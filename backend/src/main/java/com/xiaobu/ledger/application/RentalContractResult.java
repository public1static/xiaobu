package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.RentalContract;
import com.xiaobu.ledger.domain.RentalPaymentCycle;
import java.time.LocalDate;

public record RentalContractResult(
    String id,
    String homeId,
    String homeName,
    String landlordName,
    LocalDate startsOn,
    LocalDate endsOn,
    long monthlyRentCents,
    long depositCents,
    RentalPaymentCycle cycle,
    String billId,
    LocalDate firstDueOn,
    long periodAmountCents,
    String note) {
  public static RentalContractResult from(
      RentalContract contract, String homeName, LocalDate firstDueOn) {
    return new RentalContractResult(
        contract.id(),
        contract.homeId(),
        homeName,
        contract.landlordName(),
        contract.startsOn(),
        contract.endsOn(),
        contract.monthlyRentCents(),
        contract.depositCents(),
        contract.cycle(),
        contract.billId(),
        firstDueOn,
        contract.periodAmountCents(),
        contract.note());
  }
}
