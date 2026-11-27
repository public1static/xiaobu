package com.xiaobu.ledger.application;

public record LedgerMonthlyBudgetResult(
    String month, Long amountCents, long usedCents, Long remainingCents) {}
