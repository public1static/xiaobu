package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record LedgerStatementDayResult(
    LocalDate bookedOn, long incomeCents, long expenseCents, long entryCount) {}
