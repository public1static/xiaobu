package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record ReviseFixedBillPlanCommand(
    String actorId,
    String ledgerId,
    String billId,
    String name,
    LocalDate firstDueOn,
    long amountCents,
    String categoryCode,
    String accountId) {}
