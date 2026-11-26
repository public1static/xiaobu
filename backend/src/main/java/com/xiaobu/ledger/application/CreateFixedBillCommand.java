package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record CreateFixedBillCommand(
    String ledgerId,
    String actorId,
    String name,
    LocalDate firstDueOn,
    long amountCents,
    String categoryCode,
    String accountId) {}
