package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record RecordExpenseCommand(
    String ledgerId,
    String actorId,
    long amountCents,
    LocalDate bookedOn,
    String categoryCode,
    String note) {}
