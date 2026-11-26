package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.domain.RentalPaymentCycle;
import java.time.LocalDate;

public record RentalContractResponse(
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
    String landlordDistinction) {}
