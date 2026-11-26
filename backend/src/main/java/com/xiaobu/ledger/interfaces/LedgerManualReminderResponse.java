package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;
import java.time.LocalDateTime;

record LedgerManualReminderResponse(
    String id,
    String title,
    LocalDate dueOn,
    LocalDateTime remindAt,
    int leadDays,
    boolean completed) {}
