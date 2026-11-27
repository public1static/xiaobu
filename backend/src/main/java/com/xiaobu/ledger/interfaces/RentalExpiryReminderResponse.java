package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;

record RentalExpiryReminderResponse(
    String contractId,
    String homeName,
    String landlordName,
    LocalDate endsOn,
    int remindDays,
    LocalDate handledEnd,
    boolean pending) {}
