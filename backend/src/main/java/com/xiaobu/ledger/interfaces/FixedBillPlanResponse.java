package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;

public record FixedBillPlanResponse(
    String billId,
    String name,
    LocalDate firstDueOn,
    LocalDate earliestFirstDueOn,
    long amountCents,
    String categoryCode,
    String accountId) {}
