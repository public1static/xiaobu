package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerManualReminder;

public record LedgerManualReminderResult(LedgerManualReminder reminder, boolean canUndo) {}
