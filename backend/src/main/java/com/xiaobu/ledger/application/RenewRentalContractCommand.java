package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.RentalPaymentCycle;
import java.time.LocalDate;

public record RenewRentalContractCommand(
    String ledgerId,
    String actorId,
    String contractId,
    LocalDate expectedEndsOn,
    String landlordName,
    LocalDate startsOn,
    LocalDate endsOn,
    long monthlyRentCents,
    long depositCents,
    RentalPaymentCycle cycle,
    LocalDate firstDueOn,
    String note,
    String existingBillId,
    Integer remindDays) {}
