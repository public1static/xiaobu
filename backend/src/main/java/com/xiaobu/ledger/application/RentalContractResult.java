package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.FixedBill;
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
    Integer dueDayOfMonth,
    long periodAmountCents,
    String note,
    int remindDays,
    LocalDate handledEnd,
    String previousContractId,
    String nextContractId,
    String landlordContactId,
    String landlordDistinction) {
  public static RentalContractResult from(
      RentalContract contract, String homeName, FixedBill bill, String nextContractId) {
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
        bill == null ? null : bill.firstDueOnOrAfter(contract.startsOn()),
        bill == null ? null : bill.firstDueOn().getDayOfMonth(),
        contract.periodAmountCents(),
        contract.note(),
        contract.remindDays(),
        contract.handledEnd(),
        contract.previousContractId(),
        nextContractId,
        contract.landlordContactId(),
        contract.landlordDistinction());
  }
}
