package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record CompleteRentalExpiryReminderCommand(
    String ledgerId, String actorId, String contractId, LocalDate expectedEndsOn) {}
