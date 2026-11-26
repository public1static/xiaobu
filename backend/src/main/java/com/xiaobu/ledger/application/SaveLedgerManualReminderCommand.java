package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.ManualReminderFrequency;
import java.time.LocalDate;
import java.time.LocalTime;

public record SaveLedgerManualReminderCommand(
    String ledgerId,
    String actorId,
    String reminderId,
    String title,
    LocalDate dueOn,
    LocalTime time,
    int leadDays,
    ManualReminderFrequency frequency,
    Long revision) {}
