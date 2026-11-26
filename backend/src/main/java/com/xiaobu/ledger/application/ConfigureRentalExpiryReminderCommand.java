package com.xiaobu.ledger.application;

public record ConfigureRentalExpiryReminderCommand(
    String ledgerId, String actorId, String contractId, int remindDays) {}
