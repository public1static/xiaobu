package com.xiaobu.ledger.application;

import java.time.YearMonth;

public record SetLedgerMonthlyBudgetCommand(
    String ledgerId, String actorId, YearMonth month, long amountCents) {}
