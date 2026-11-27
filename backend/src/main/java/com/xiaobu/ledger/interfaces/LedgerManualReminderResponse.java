package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.domain.ManualReminderFrequency;
import java.time.LocalDate;
import java.time.LocalDateTime;

record LedgerManualReminderResponse(
    String id,
    String title,
    LocalDate dueOn,
    LocalDateTime remindAt,
    int leadDays,
    boolean completed,
    ManualReminderFrequency frequency,
    LocalDate anchorOn,
    long revision,
    boolean stopped,
    boolean canUndo) {}
