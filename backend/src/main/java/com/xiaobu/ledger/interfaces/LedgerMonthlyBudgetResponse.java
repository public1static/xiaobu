package com.xiaobu.ledger.interfaces;

record LedgerMonthlyBudgetResponse(
    String month, Long amountCents, long usedCents, Long remainingCents) {}
