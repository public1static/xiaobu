package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.RentalPaymentCycle;
import java.time.LocalDate;

public record ListRentalBillPlansQuery(
    String ledgerId,
    String actorId,
    RentalPaymentCycle cycle,
    LocalDate startsOn,
    LocalDate endsOn,
    int page) {}
