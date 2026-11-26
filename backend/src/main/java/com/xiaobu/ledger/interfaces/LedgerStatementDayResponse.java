package com.xiaobu.ledger.interfaces;

import java.time.LocalDate;

record LedgerStatementDayResponse(
    LocalDate bookedOn, long incomeCents, long expenseCents, long entryCount) {}
